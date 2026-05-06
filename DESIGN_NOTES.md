# DESIGN_NOTES.md — 日股支援與補資料策略設計方案

> 文件目的：在開始寫程式前，確認實作方向與關鍵決策。
> 方案確認後才進入實作。
> 撰寫日期：2026-04-18

---

## 零、現況分析

### 現有補資料行為（問題點）

`StockService` 裡的兩個主要查詢端點，目前行為是：

```
queryStockTurnData()   → 每次呼叫都執行 forceUpdateStockDatas()  → 呼叫 histock 外部 API
queryStockTurnDataK()  → 每次呼叫都執行 updateStockData().get()  → 呼叫 histock 外部 API
```

原本有用 `bizDateService.isBizDate()` 控制只在交易日才更新，**但這段程式碼已被註解掉**，
導致目前每一次前端查詢都會發一次外部 HTTP 請求，造成：
- 查詢延遲（等外部 API 回應）
- 重複寫入相同資料（浪費 DB I/O）
- 若 histock 服務不穩定，查詢全部受影響

### 日股現況

- `Stocks` 表：已加入 `market` 欄位（2026-04-18 完成），16 支日股已用 market='JP' 寫入。
- `StockData` 表：stockId 使用 `.T` 後綴格式（如 `4182.T`），由 Python 爬蟲直接寫入，共 33,111 筆。
- `StockData` 表：**沒有 `market` 欄位**，複合 PK 仍為 `stockId + dataType + dataTime`。
- Spring Boot：尚未對 `market='JP'` 的股票做任何特殊路由。

### stockId 格式決策（日股）

SYSTEM_OVERVIEW.md 原規劃為「stockId 純數字，用 market 區分」，但：

1. 資料已用 `.T` 後綴寫入（如 `4182.T`），遷移需要清空重寫。
2. 日本股票代碼 4 位數字，**與台灣股票代碼確實有重疊**（例如台股有 `4182`，日股也有 `4182.T`）。
3. `.T` 後綴剛好等於 yfinance 的原始 ticker，Python 爬蟲不需要額外轉換。

**本次決策：維持 `.T` 後綴（`4182.T`）作為日股 stockId。**
- 不需要在 `StockData` 加入 `market` 欄位（避免 5 個 table 的 PK 大規模異動）。
- Spring Boot 查詢路由依據 `Stocks.market` 欄位，不依賴 stockId 格式。
- 此決策為本次範圍（方向A）的務實選擇，未來如需正式多市場架構再執行 Phase 2 PK 遷移。

---

## 一、補資料策略：Cache-first

### 1.1 判斷邏輯

每次收到查詢請求（不論台股或日股）時，先查 DB，再決定要不要呼叫外部 API：

```
┌──────────────────────────────────────────────┐
│  收到查詢 (stockId, dataType, dataCount)      │
└─────────────────────────┬────────────────────┘
                          │
                          ▼
          查 StockData 最新一筆（MAX dataTime）
                          │
          ┌───────────────┼────────────────────┐
          │               │                    │
      無資料          有資料但              有資料且
      (count=0)       今日未更新            今日已更新
          │               │                    │
          ▼               ▼                    ▼
       全量補抓        只補今日            直接回傳 DB
      (10y / all)    (最近 5 天)           (不呼叫外部)
```

### 1.2 「今日已更新」的判斷條件

日本股市（JST UTC+9）與台灣時間（UTC+8）相差 1 小時。
日股收盤 15:30 JST = 14:30 台灣時間。

判斷基準統一使用**台灣時間 08:00 的毫秒 epoch**：

```python
# 取得今天的 dataTime 基準值
from datetime import datetime
import pytz

TZ_TAIPEI = pytz.timezone("Asia/Taipei")
today_base_ms = int(
    datetime.now(TZ_TAIPEI)
    .replace(hour=8, minute=0, second=0, microsecond=0)
    .timestamp() * 1000
)

# 若 DB 最新 dataTime >= today_base_ms → 今日已更新
```

### 1.3 三種情境的行為對照

| 情境 | DB 狀態 | 動作 | 說明 |
|------|--------|------|------|
| 全新股票 | `count = 0` | **全量補抓**，yfinance `period="10y"` | 首次寫入歷史資料 |
| 有資料但未更新 | `latest dataTime < today 08:00` | **增量補抓**，yfinance `period="5d"` | 只補最近交易日 |
| 今日已更新 | `latest dataTime >= today 08:00` | **直接回傳** | 零外部呼叫 |

### 1.4 本次範圍限制

- **方向A：只改 JP 股票的補資料邏輯**，由 Python FastAPI 負責。
- 台股（TW）的 `forceUpdateStockDatas()` 呼叫時機問題**不在本次範圍內**，維持現狀。
- 未來若要統一改台股補資料策略，可在 Spring Boot `StockCommonService` 中加入相同的 Cache-first 判斷。

---

## 二、日股支援：方向A 實作方案

### 2.1 整體架構

```
前端 App（JSONP）
        │
        ▼
Spring Boot（port 8089）
        │
        │  Stocks.market = 'JP' ?
        │
   YES  │  NO
   ┌────┘  └──────────────────────────────┐
   ▼                                      ▼
Python FastAPI                      現有 histock 流程
（port 8090）                        (StockCommonService)
        │                                 │
        │  Cache-first 判斷               │
        │  yfinance 補資料                │
        ▼                                 ▼
    MySQL StockData               MySQL StockData
        └──────────────┬───────────────┘
                       ▼
              Spring Boot 讀取資料
                       │
                       ▼
              JSONP 回傳給前端
```

### 2.2 Python FastAPI — `collector/jp_api.py`

#### 端點設計

```
POST /api/fetch/jp
Content-Type: application/json

Request Body:
{
  "stockId":  "4182.T",
  "dataType": "D"         // D / W / M
}

Response:
{
  "status":   "ok" | "skipped" | "error",
  "action":   "full" | "incremental" | "no_action",
  "written":  2464,
  "message":  "說明文字"
}
```

#### 內部流程

```
1. 查 DB：SELECT MAX(dataTime), COUNT(*)
          FROM StockData
          WHERE stockId = :stockId AND dataType = :dataType

2. 判斷情境
   ├─ count = 0          → period = "10y"（全量）
   ├─ max < today 08:00  → period = "5d" （增量）
   └─ max >= today 08:00 → return {status:"skipped", action:"no_action"}

3. 呼叫 yfinance 抓資料
   ticker = yf.Ticker(stockId)
   hist   = ticker.history(period=period, auto_adjust=True)

4. 依 CLAUDE.md §四 規範轉換時間
   ─ dataTime = 收盤日 08:00 台灣時間的毫秒 epoch（yfinance 日期直接用，不 -1 day）
   ─ dataDate = 同一時刻的 DATETIME 字串

5. INSERT ... ON DUPLICATE KEY UPDATE 寫入 StockData

6. 回傳結果
```

#### 檔案位置

```
stockapp/
└── collector/
    ├── fetch_nikkei225.py   # 已存在（批次歷史載入）
    ├── jp_api.py            # 新增（FastAPI 服務主體）
    └── db.py                # 新增（共用 DB 連線工具）
```

### 2.3 Spring Boot — 路由修改

#### 修改位置

只修改 `StockService.java` 的以下兩個方法入口：
- `queryStockTurnData()`（多空戰K、多空中軸查詢）
- `queryStockTurnDataK()`（轉折K線查詢）

台股流程（`forceUpdateStockDatas`）**完全不動**。

#### 新增邏輯（在呼叫外部資料前插入）

```java
// 在 forceUpdateStockDatas() 呼叫之前插入

Optional<Stocks> stockOpt = stocksRepository.findById(stockId);
if (stockOpt.isPresent() && "JP".equals(stockOpt.get().getMarket())) {
    // 日股 → 呼叫 Python FastAPI 補資料，取代 forceUpdateStockDatas
    jpDataFetchService.ensureData(stockId, dataType.getCode());
    // 不再呼叫 forceUpdateStockDatas()，直接往下讀 DB
} else {
    // 台股/其他 → 維持現有流程（不動）
    forceUpdateStockDatas(stockId, dataType, dataCount);
}
```

#### 新增 Service — `JpDataFetchService.java`

```java
@Service
public class JpDataFetchService {

    // 對 Python FastAPI 發 POST 請求
    public void ensureData(String stockId, String dataType) {
        // HTTP POST http://localhost:8090/api/fetch/jp
        // Body: { stockId, dataType }
        // 同步等待回應（timeout 30s）
    }
}
```

#### 設定檔 — `application.yml` 新增

```yaml
jp-fetcher:
  url: http://localhost:8090/api/fetch/jp
  timeout-seconds: 30
```

### 2.4 `Stocks` 表 — `market` 欄位的使用

Spring Boot `Stocks.java` 需新增 `market` 欄位（配合已完成的 DB ALTER TABLE）：

```java
@Column(name = "market", nullable = false, length = 5)
private String market = "TW";
```

`StocksRepository.findById(stockId)` 已可直接使用，不需新增查詢方法。

---

## 三、資料流程圖

### 3.1 日股查詢完整流程（方向A 實作後）

```
使用者點擊查詢（si=4182.T, dt=D, dn=60）
        │
        │ $.ajax JSONP
        ▼
GET /stock/queryStockTurnData?si=4182.T&dt=D&dn=60
        │
        ▼
StockController.queryStockTurnData()
        │
        ▼
StockService.queryStockTurnData()
        │
        ├─ stocksRepository.findById("4182.T")
        │  → Stocks { market: "JP" }
        │
        ▼  market = "JP"
JpDataFetchService.ensureData("4182.T", "D")
        │
        │ HTTP POST (sync, timeout 30s)
        ▼
Python FastAPI（port 8090）POST /api/fetch/jp
        │
        ├─ 查 DB MAX(dataTime)
        │
        ├─ 情境判斷
        │   ├─ no_action → 立即回傳
        │   └─ incremental → yfinance period=5d → INSERT DB
        │
        ▼
回傳 { status, action, written }
        │
        ▼ (回到 Spring Boot)
StockService 繼續往下
        │
        ├─ stockDataRepository.findDataDesc("4182.T", "D", 60)
        │  （直接讀 DB，資料已是最新）
        │
        ▼
組裝 ChartData / TurnKData 回傳
        │
        ▼
JSONP Response
        │
        ▼
前端 Highcharts 渲染 K 線圖
```

### 3.2 台股查詢流程（不變）

```
使用者點擊查詢（si=2330, dt=D, dn=60）
        │
        ▼
StockService.queryStockTurnData()
        │
        ├─ stocksRepository.findById("2330")
        │  → Stocks { market: "TW" }
        │
        ▼  market != "JP"
forceUpdateStockDatas()（現有流程，不動）
        │
        ├─ StockCommonService.updateStockData()
        │  → histock.tw API 抓取
        │
        ▼
讀 DB → 組裝回傳
```

### 3.3 Python FastAPI 補資料流程（Cache-first 細節）

```
POST /api/fetch/jp { stockId: "4182.T", dataType: "D" }
        │
        ▼
查 DB:
SELECT MAX(dataTime) as latest, COUNT(*) as cnt
FROM StockData
WHERE stockId='4182.T' AND dataType='D'
        │
        ├─ cnt = 0
        │    → period = "10y"
        │    → action = "full"
        │
        ├─ latest < today_08:00_ms
        │    → period = "5d"
        │    → action = "incremental"
        │
        └─ latest >= today_08:00_ms
             → return {status:"skipped", action:"no_action", written:0}
        │
        ▼（full 或 incremental 才繼續）
yfinance.Ticker("4182.T").history(period=period)
        │
        ▼
依規範轉換時間：
  trade_date → 台灣 08:00 → epoch ms
        │
        ▼
INSERT INTO StockData ... ON DUPLICATE KEY UPDATE
        │
        ▼
return {status:"ok", action:..., written:N}
```

---

## 四、需要修改的檔案清單

### Python（新增）

| 檔案 | 類型 | 說明 |
|------|------|------|
| `collector/jp_api.py` | 新增 | FastAPI 主體，含 Cache-first 補資料邏輯 |
| `collector/db.py` | 新增 | 共用 pymysql 連線 pool |

### Spring Boot（修改）

| 檔案 | 修改性質 | 說明 |
|------|---------|------|
| `entity/Stocks.java` | 新增欄位 | 加入 `private String market = "TW"` + getter/setter |
| `service/JpDataFetchService.java` | 新增檔案 | 對 Python FastAPI 發 HTTP POST |
| `service/StockService.java` | 修改 2 處 | `queryStockTurnData()` 和 `queryStockTurnDataK()` 加入 market 判斷 |
| `resources/application.yml` | 新增設定 | `jp-fetcher.url` 和 `timeout-seconds` |

### 不需要修改的檔案

| 檔案 | 理由 |
|------|------|
| `entity/StockDataId.java` | StockData 複合 PK 不加 market（維持 `.T` 後綴策略） |
| `StockDataMA / EMA / Turn / RSI` | 同上，不需要改動 |
| `StockController.java` | API 介面不變，修改在 Service 層 |
| `StockApp/` 前端所有頁面 | stockId 直接傳 `4182.T`，查詢 API 介面不變 |
| `StockCommonService.java` | 台股補資料流程不動 |

---

## 五、風險與限制

| 風險 | 說明 | 緩解方式 |
|------|------|---------|
| Python FastAPI 服務未啟動 | Spring Boot 呼叫失敗 | 加 try-catch，失敗時直接讀 DB 現有資料（降級回傳） |
| yfinance 被 Yahoo 限速 | 頻繁查詢時補資料失敗 | Cache-first 策略本身就能大幅減少呼叫次數 |
| 日股假日判斷 | 非交易日查詢會判斷「今日未更新」而發出無謂請求 | 日股當日若 yfinance 回傳空資料，保持 DB 現有資料不清空 |
| Spring Boot 同步呼叫 Python | 若 Python 慢，查詢延遲增加 | timeout 30s 保護；日後可改非同步 |

---

## 六、實作順序建議

```
Phase A1：Python FastAPI
  1. collector/db.py            共用 DB 工具
  2. collector/jp_api.py        FastAPI 主體 + Cache-first 邏輯
  3. 手動測試 POST /api/fetch/jp，驗證三種情境（全量/增量/跳過）

Phase A2：Spring Boot
  4. entity/Stocks.java         加入 market 欄位
  5. service/JpDataFetchService.java  HTTP 呼叫 Python
  6. service/StockService.java  加入 market 判斷，接入 JpDataFetchService

Phase A3：整合測試
  7. 啟動 Python FastAPI（port 8090）
  8. 啟動 Spring Boot（port 8089）
  9. 用前端查詢 4182.T，確認流程正確
 10. 用前端查詢 2330，確認台股不受影響
```

---

## 七、本文件待確認的決策點

以下六個決策點請在實作前確認：

| # | 決策點 | 本文件建議 | 待確認 |
|---|--------|----------|--------|
| 1 | 日股 stockId 格式 | 維持 `.T` 後綴（`4182.T`）| ☐ |
| 2 | Python FastAPI port | 8090 | ☐ |
| 3 | Spring Boot 呼叫 Python 的方式 | 同步（RestTemplate），timeout 30s | ☐ |
| 4 | 台股補資料邏輯 | 本次不動，維持現有 forceUpdate | ☐ |
| 5 | Python FastAPI 的 Stocks 主檔寫入 | 由 jp_api.py 自行 UPSERT（同 fetch_nikkei225.py 做法） | ☐ |
| 6 | 日股增量補抓的 period 參數 | `"5d"`（yfinance）| ☐ |

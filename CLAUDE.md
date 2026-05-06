# CLAUDE.md — Stock Analysis 專案開發參考文件

> 最後更新：2026-04-27

---

## 零、目前完成狀態（2026-04-19 ~ 04-20）

### DB 層

| 狀態 | 項目 |
|------|------|
| ✅ | `Stocks` 加 `market` 欄位（`TW` / `JP` / `US`） |
| ✅ | 5 張大表（StockData、StockDataMA、StockDataEMA、StockDataTurn、StockDataRSI）加 secondary index `idx_stock_type_time` |
| ✅ | `volume` / `volume2` 欄位由 INT 改為 BIGINT |
| ✅ | `stock_fundamental` 新表（三市場基本面資料：PE、EPS、殖利率等） |

### Python 層

| 狀態 | 項目 |
|------|------|
| ✅ | `jp_api.py` FastAPI（port 8090）— Cache-first 補資料架構 |
| ✅ | `fundamental.py` 三市場基本面爬蟲（TW / JP / US） |
| ✅ | `fetch_nikkei225.py` 日股批次爬蟲（16 支） |
| ✅ | 日股盤後排程（台灣時間 15:00 自動更新） |
| ✅ | `yfinance` 升級至 1.3.0 |
| ✅ | `stock-analysis` Docker image 升級至 1.2 |

### Spring Boot 層

| 狀態 | 項目 |
|------|------|
| ✅ | `Stocks.java` 加 `market` 欄位 |
| ✅ | `JpDataFetchService.java`（呼叫 Python FastAPI 取得日股資料） |
| ✅ | `FundamentalController.java`（`GET /fundamental/query`） |
| ✅ | `JpStockScheduler.java`（每日 15:00 自動觸發日股更新） |
| ✅ | `StocksRepository.java` 加 `findByMarket()` |
| ✅ | `deploy.sh` 自動部署腳本 |

### 前端層

| 狀態 | 項目 |
|------|------|
| ✅ | 統一頂部 navbar（所有頁面） |
| ✅ | `index.html` 整合首頁（手機優先設計） |
| ✅ | 下拉選單依市場分組（台股 / 日股 / 美股） |
| ✅ | Tab 頁籤：多空戰K / 多空中軸 / 基本面 / 除權息 / 轉折分析 |
| ✅ | 日 / 週 / 月 切換正常 |
| ✅ | 手機版 ☰ 漢堡選單 |
| ✅ | Cache busting（`?v=20260420`） |

### 待辦事項

| 優先度 | 項目 |
|--------|------|
| ✅ | `www` 資料夾清理（Tomcat webapps 舊檔） |
| 中 | 日股清單擴充（目前 16 支） |
| 中 | 美股基本面資料補齊 |
| 中 | 台股基本面整合現有六張財務表 |
| ✅ | DB 分區重設計（Phase 2）— 5 張表全部完成 |
| ⬜ | 觀察 1-2 天後執行 `21_drop_backup_tables.sql` 清理備份表 |

---

## 一、整體目錄結構

```
stock-analysis/
├── python/      ← Python 爬蟲 + FastAPI（Dash 儀表板）
├── StockApp/    ← HTML5 前端（jQuery + Highcharts）
└── stockserver/ ← Spring Boot 後端（REST API + JPA）
```

各子目錄有獨立 README，本文件為跨子系統的整合規範。

---

## 二、Python 環境（python/）

### 2.1 執行環境

| 項目 | 值 |
|------|-----|
| Container 名稱 | `stock-analysis` |
| Python 版本 | 3.11 |
| 對外 Port | 8050 |
| 工作目錄 | `/app` |
| 資料目錄 | `/app/data` |
| Docker Network | `dev_master_network` |

### 2.2 資料庫連線

| 項目 | 值 |
|------|-----|
| Host | `mysql55` |
| Port | `3306` |
| Database | `stockapp` |
| 連線資訊來源 | `/app/.env` |

`.env` 格式：
```
DB_HOST=mysql55
DB_PORT=3306
DB_NAME=stockapp
DB_USER=your_user
DB_PASSWORD=your_password
```

> **規範：資料庫連線資訊只從 `.env` 讀取，不 hardcode 帳密。**

### 2.3 技術棧

| 層級 | 套件 |
|------|------|
| 畫面框架 | `dash` + `dash-bootstrap-components` (DARKLY theme) |
| 圖表 | `plotly` |
| 資料收集 | `yfinance` |
| 技術指標 | `ta` |
| 回測 | `vectorbt` |
| 資料庫 | `sqlalchemy` + `pymysql` |
| 排程 | `apscheduler` |
| 設定檔 | `python-dotenv` |
| Log | `loguru` |

### 2.4 目錄結構

```
python/
├── CLAUDE.md                → 指向本文件
├── main.py                  # Dash app 入口，port 8050
├── requirements.txt
├── .env                     # 資料庫連線資訊（不進 Git）
├── collector/
│   ├── __init__.py
│   ├── price.py             # 抓價格/K線資料 (yfinance)
│   ├── fundamental.py       # 抓財報、PE、EPS
│   ├── fetch_nikkei225.py   # 日股日線爬蟲
│   └── scheduler.py         # APScheduler 盤後自動更新
├── db/
│   ├── __init__.py
│   ├── models.py            # SQLAlchemy ORM models
│   └── session.py           # DB session 管理
├── analysis/
│   ├── __init__.py
│   ├── technical.py         # 技術指標計算 (MA, RSI, MACD, BB)
│   ├── fundamental.py       # 本益比、殖利率、財報比較
│   └── correlation.py       # 跨市場相關性分析
├── backtest/
│   ├── __init__.py
│   └── engine.py            # vectorbt 回測引擎
└── pages/
    ├── __init__.py
    ├── home.py              # 首頁
    ├── technical.py         # 技術分析頁
    ├── fundamental.py       # 基本面頁
    ├── correlation.py       # 相關性頁
    └── backtest.py          # 回測頁
```

### 2.5 股票代碼規則

| 市場 | 格式 | 範例 |
|------|------|------|
| 美股 | 直接代碼 | `AAPL`, `TSLA`, `NVDA` |
| 台股 | 代碼 + `.TW` | `2330.TW`, `2317.TW` |
| 日股 | 代碼 + `.T` | `7203.T`, `6758.T` |

### 2.6 資料更新策略

#### 排程更新（自動）
- 台股：每天 14:00 觸發（收盤後 30 分鐘）
- 日股：每天 15:30 觸發（收盤後 30 分鐘）
- 美股：每天 06:00 觸發（台灣時間，美東收盤後）
- 由 `collector/scheduler.py` 的 APScheduler 負責

#### 即時更新（使用者觸發）
- 使用者查詢某支股票時，檢查 DB 內該股票最後更新時間
- 若最後更新不是今天，先呼叫 `yfinance` 抓最新資料寫入 DB，再回傳給前端
- 若已是今天的資料，直接從 DB 讀取回傳

#### 補資料策略 — 動態補抓期間計算

```python
from datetime import date

def calc_fetch_period(last_update_date):
    if last_update_date is None:
        return "10y"          # 全新股票，抓十年歷史
    days_missing = (date.today() - last_update_date).days
    if days_missing <= 0:
        return None           # 今天已更新，略過
    return f"{days_missing + 15}d"  # 加 15 天緩衝應對長假 / 停機
```

### 2.7 Python 開發規範

1. 所有 Python 檔案的 public method 都要有完整 docstring
2. 錯誤處理統一使用 `loguru` logger，不使用 `print`
3. 資料庫連線資訊只從 `.env` 讀取，不 hardcode
4. 新增套件先更新 `requirements.txt`，並告知需要重新 build image
5. Dash callback 盡量加上 `prevent_initial_call=True`

### 2.8 常用指令

```bash
# 查看 log
docker logs -f stock-analysis

# 進入 container
docker exec -it stock-analysis bash

# 重啟 container
docker restart stock-analysis

# 更新套件後重新 commit image
docker commit stock-analysis stock-analysis:1.1
```

---

## 三、系統架構（StockApp + stockserver）

### 3.1 架構總覽

```
stockapp/
├── StockApp/               # 前端（HTML5 + jQuery + Highcharts）
│   └── assets/www/         # 所有頁面與靜態資源
│       ├── js/
│       │   └── stockapp.js # 共用 JS：選單、股票下拉、圖表函式
│       ├── css/
│       ├── vendor/         # 第三方函式庫
│       └── *.html          # 各功能頁面
└── stockserver/            # 後端（Spring Boot 3.x + JPA/Hibernate）
    └── src/main/java/com/stockapp/stockserver/
        ├── controller/     # REST API 端點
        ├── service/        # 商業邏輯
        ├── entity/         # JPA Entity（對應 DB table）
        ├── enums/          # 列舉型別
        ├── repo/           # Spring Data JPA Repository
        ├── schedule/       # 排程工作
        ├── utils/          # 工具類別
        └── config/         # Spring 設定
```

### 3.2 技術棧

| 層級 | 技術 |
|------|------|
| 前端框架 | HTML5 + jQuery + Bootstrap 3 |
| 圖表 | Highcharts StockChart |
| 選單 | bootstrap-select (selectpicker) |
| 跨域請求 | JSONP（`$.ajax` + `dataType: "jsonp"`） |
| 後端框架 | Spring Boot 3.x |
| ORM | JPA / Hibernate（`MySQL55Dialect`） |
| 資料庫 | MySQL（Host: `1.34.57.147:3305`, DB: `stockapp`） |
| 連線池 | HikariCP（max: 10, min-idle: 5） |
| 非同步 | `@EnableAsync` + `Future<CallbackResult>` |
| 排程 | `@EnableScheduling` + `@Scheduled(cron = ...)` |
| 伺服器 port | 8089，context-path: `/StockServer` |

### 3.3 前後端溝通方式

- **全部使用 JSONP**（因前端是 Cordova/PhoneGap 封裝的 App，有跨域限制）
- API base URL：`https://stock.bignoodle.net/StockServer/`
- 前端固定 callback 名稱為 `call` 或 `call2`（視 API 而定）

---

## 四、資料庫 Table 清單與用途

> Hibernate `ddl-auto` 設定請參考實際 application.yml。無獨立的 SQL migration 檔案，schema 由 Hibernate Entity 管理。

### 主資料表

#### `Stocks` — 股票主檔
| 欄位 | 型別 | 說明 |
|------|------|------|
| stockId | VARCHAR(50) PK | 股票代碼（見格式規範） |
| stockName | VARCHAR(100) NOT NULL | 中文名稱 |
| stockEngName | VARCHAR(200) | 英文名稱 |
| stockShortName | VARCHAR(50) | 簡稱 |
| stockType | VARCHAR(10) NOT NULL | 股票類型 |
| status | INT | 0=下市櫃, 1=持續買賣 |
| wants | INT | 是否有權證（0/1） |
| futures | INT | 是否有期貨（0/1） |
| futuresId | VARCHAR | 期貨代號 |
| futuresId2 | VARCHAR | 小型期貨代號 |
| originalRatio | DECIMAL | 原始保證金比例 |
| originalRatio2 | DECIMAL | 小型原始保證金比例 |
| capital | DECIMAL | 股本 |
| products | VARCHAR(500) | 產品描述 |
| lastUpdateTime | DATETIME | 最後更新時間 |
| lastUpdateTimeHistock | DATETIME | Histock 最後更新時間 |

#### `StockData` — K 線價量資料（主資料表）
| 欄位 | 型別 | 說明 |
|------|------|------|
| stockId | VARCHAR(50) PK(1/3) | 股票代碼 |
| dataType | VARCHAR(2) PK(2/3) | 資料週期（D/W/M） |
| dataTime | BIGINT PK(3/3) | 時間戳（台灣早上 08:00 為基準） |
| open | DOUBLE | 開盤價 |
| close | DOUBLE | 收盤價 |
| high | DOUBLE | 最高價 |
| low | DOUBLE | 最低價 |
| volume | INT | 成交量 |
| volume2 | INT | 成交量（第二欄，用途依資料源而定） |
| dataDate | DATETIME | 對應日期 |

**Composite PK 類別：`StockDataId`（`@Embeddable`）**

關聯：
- `StockData` → `StockDataMA`（`@OneToOne`, CascadeType.REMOVE）
- `StockData` → `StockDataEMA`（`@OneToOne`, CascadeType.REMOVE）

#### `StockDataMA` — 移動平均線（SMA）
| 欄位 | 說明 |
|------|------|
| id (StockDataId) | 複合 PK，與 StockData 相同 |
| ma3, ma5, ma8, ma10, ma13 | 短期 SMA |
| ma20, ma21 | 月均線 |
| ma34, ma55, ma60 | 中期 SMA（含 Fibonacci 數列） |
| ma89, ma144, ma233 | 長期 Fibonacci SMA |
| dataDate | 日期 |

#### `StockDataEMA` — 指數移動平均線（EMA）
| 欄位 | 說明 |
|------|------|
| id (StockDataId) | 複合 PK |
| ema3, ema5, ema7, ema10, ema13 | 短期 EMA |
| ema21, ema34, ema53, ema89 | 中期 EMA |
| ema144, ema233 | 長期 EMA |

#### `StockDataTurn` — 多空轉折技術指標
| 欄位 | 說明 |
|------|------|
| id (StockDataId) | 複合 PK |
| middle | 日多空中軸 |
| value1 | DD 開盤破站不上空 |
| value2 | 易多空線 |
| value3 | 超強 |
| value4 | 乖離短賣 |
| value5 | 高控多停利 |
| value6 | 低控空回補 |
| value7 | 嘎空點 |
| value8 | D 殺多 |
| value9 | 末跌 |
| value10 | 日續跌 |
| value11 | 破續跌續空 |
| value12 | 碰主跌＝搶短點 |
| value14 | 碰主跌 3 |
| value15 | 彈仍跌（放空） |
| value16 | 回跌 |
| value17 | 回檔買點／反彈賣點 |
| value18 | 時 K 反彈不該過中軸 |
| value19 | 盤跌 |
| value20 | T 盤漲 |
| value21 | 反彈峰 B |
| value22 | 續漲不破續多 |
| value23 | 過起漲 3 |
| value24 | 過起漲 |
| value25 | 日續漲 |
| value26 | 主升 |
| value27 | 強波 2000 點 |
| value29 | 超跌 17 |
| value30 | 超跌 16 |
| value31 | 超跌 15 |

> **注意**：value13 和 value28 不存在（跳號），修改時勿以「連續數字」假設。

#### `StockDataRSI` — RSI 指標
> Entity 存在，細節需讀 `StockDataRSI.java`。

#### `StockExclude` — 除權息資料
| 欄位 | 說明 |
|------|------|
| stockId (PK 1/2) | 股票代碼 |
| excludeDate (PK 2/2) | 除權息日期 |
| excludeDividend | 配息金額 |
| excludeRight | 配股金額 |
| cashYields | 現金殖利率 |
| yields | 殖利率 |
| couponRate | 配息率 |
| afterExPrice | 除權後參考價 |
| excludeDividendRate | 除息趴數 |
| excludeRightRate | 除權趴數 |
| rdRate | 除權息趴數 |
| rdGoldRate | 權息黃金比例 |
| stockName | 股票名稱（冗餘，方便查詢） |
| price | 當時價格 |
| futures, wants | `@Transient`，不存入 DB，由程式計算 |

### 期貨 / 選擇權資料表

#### `FuturesOpen` — 期貨未平倉資料
| 欄位 | 說明 |
|------|------|
| dataTime (PK) | 時間戳 |
| dataDate | 日期 |
| buyMonthTop5/10/5S/10S | 買方近月 Top5/10（含標準差版本） |
| buyAllTop5/10/5S/10S | 買方全月 Top5/10 |
| sellMonthTop5/10/5S/10S | 賣方近月 Top5/10 |
| sellAllTop5/10/5S/10S | 賣方全月 Top5/10 |
| buyOpens1/2/3 | 買方未平倉（1=自營商, 2=投信, 3=外資） |
| sellOpens1/2/3 | 賣方未平倉 |
| buyOpens1/2/3Small | 買方小型期貨未平倉 |
| sellOpens1/2/3Small | 賣方小型期貨未平倉 |
| monthTotalOpen, allTotalOpen | 近月/全部總未平倉 |

#### `OptionsOpen` — 選擇權未平倉資料
| 欄位 | 說明 |
|------|------|
| dataTime (PK) | 時間戳 |
| dataDate | 日期 |
| bCallOI/TotalPrice/AvgPrice/Diff | 買權買方資料 |
| bPutOI/TotalPrice/AvgPrice/Diff | 賣權買方資料 |
| sCallOI/TotalPrice/AvgPrice/Diff | 買權賣方資料 |
| sPutOI/TotalPrice/AvgPrice/Diff | 賣權賣方資料 |

### 其他輔助表

| Table | 用途 |
|-------|------|
| `StockGap` | 跳空缺口記錄 |
| `StockBuySell` | 買賣點資料 |
| `StockPriceLevel` | 股價水準分析 |
| `StockPriceLevelData` | 股價水準明細 |
| `StockFinanDetail` | 財務明細 |
| `StockFinanRatio` | 財務比率 |
| `StockBalanceSheet` | 資產負債表 |
| `StockCashFlow` | 現金流量表 |
| `StockDividend` | 股利資料 |
| `StockWants` | 權證資料 |
| `StockFutures` | 個股期貨資料 |
| `StockRawData` | 原始資料暫存 |
| `StockDataTurtle` | 海龜策略資料 |
| `BizDate` | 交易日曆 |
| `Menu` | 前端選單設定 |
| `Webhook` | Webhook 設定（含 market 欄位） |
| `Stockai` | AI 分析資料（含 market 欄位） |
| `Singal` | 訊號記錄 |
| `SystemParam` | 系統參數 |
| `MailTarget` | 郵件通知對象 |
| `UserModel` | 使用者（登入相關） |

---

## 五、stockId / dataType / market 格式規範

### 5.1 stockId 格式

**目前規範：純英數字，無市場後綴**

```
台灣上市/上櫃：純數字   e.g. "2330", "0050", "2454"
大盤指數：     "0000"
台灣期貨：     "FITX", "FITXP"（小型台指）
摩根台灣：     "STW"
美國指數：     "YM"（迷你道瓊）, "DJI", "NASDAQ", "SP500"
其他：         "BR"（半導體指數）
```

- DB 欄位長度：VARCHAR(50)
- 程式中呼叫 `.toUpperCase()` 轉大寫後使用
- 資料來源：`http://histock.tw/` 的代碼系統
- `StockDataId.java:11` 備註：`"目前id使用http://histock.tw/的代碼(No)"`

**日股若要加入的衝突問題：** 日本股票代碼（如 Toyota `7203`）與台灣股票代碼（如 `7203`）**數字相同會衝突**，需要在 PK 中加入 market 欄位區分。

### 5.2 dataType 格式

**來源：`StockDataTypeType.java`（Enum）**

| DB 值 | Enum 名稱 | 說明 |
|-------|----------|------|
| `"D"` | `DAYILY` | 日線（預設值） |
| `"W"` | `WEEK` | 週線 |
| `"M"` | `MONTH` | 月線 |
| `UNKNOWN_STR_CODE` | `UNKNOWN` | 未知，會 fallback 到 DAYILY |

> **注意**：HTML 頁面中有出現 `"Y"`（年線），但 Enum 中**沒有定義** YEAR。若要支援年線需同步新增 Enum 值。

DB 欄位：`VARCHAR(2) NOT NULL`，StockDataId 的一部分（複合 PK）。

### 5.3 market 欄位（現況 vs 規劃）

**現況：** 主要業務 entity（Stocks、StockData、StockDataTurn 等）**沒有** market 欄位。系統隱含所有資料為台灣市場。

**已有 market 欄位的 entity：**
- `Webhook.java`（WebhookId 複合 PK 的一部分）
- `Stockai.java`

**規劃新增方向：**
- `Stocks.market`：VARCHAR(5)，值為 `"TW"` / `"JP"` / `"US"`
- `StockDataId.market`：加入複合 PK，讓不同市場的相同代碼不衝突
- 影響所有使用 `StockDataId` 的 table：`StockData`、`StockDataMA`、`StockDataEMA`、`StockDataTurn`、`StockDataRSI`

---

## 六、資料庫時間欄位規範

> **這是整個系統最重要的資料規範之一。** 所有爬蟲、資料寫入、外部整合，都必須嚴格遵守此規則，否則會與現有資料格式不一致，導致前端 Highcharts 時間軸錯誤、複合 PK 衝突、查詢結果異常。

### 6.1 dataType 合法值

| 值 | 意義 | Java Enum | 備註 |
|----|------|----------|------|
| `"D"` | 日線 | `DAYILY` | 預設值 |
| `"W"` | 週線 | `WEEK` | |
| `"M"` | 月線 | `MONTH` | |
| `"Y"` | 年線 | （未定義） | 前端 HTML 有此選項，後端 Enum **尚未實作**，傳入會 fallback 到日線 |

DB 欄位型別：`VARCHAR(2) NOT NULL`，為複合 PK（StockDataId）的一部分。

---

### 6.2 dataTime 計算規則

**型別：** `BIGINT`，單位為**毫秒**（epoch milliseconds）  
**時區：** 全部以 **`Asia/Taipei`（UTC+8）** 為基準  
**時刻：** 全部固定為當天（或當週/當月基準日）的 **08:00:00.000**

#### 各週期的基準日計算

| dataType | 基準日 | 時刻 | 說明 |
|---------|--------|------|------|
| `"D"` 日線 | histock 回傳日期的**前一天** | 08:00 台灣時間 | histock 的 timestamp 對應收盤日隔天，需 `-1 day` 修正 |
| `"W"` 週線 | 該筆資料所屬週的**週一** | 08:00 台灣時間 | 用 `previousOrSame(MONDAY)` 計算 |
| `"M"` 月線 | 該筆資料所屬月份的 **1 日** | 08:00 台灣時間 | `withDayOfMonth(1)` |

> **yfinance 注意**：yfinance 回傳的日期是收盤日本身（非隔天），日線不需要 -1 day。histock 才需要 -1 day。

#### Java 實作參考（`AbstractService.java`）

```java
LocalDateTime dateTime = LocalDateTime.ofEpochSecond(
    timestamp, 0,
    ZoneId.of("Asia/Taipei").getRules().getOffset(LocalDateTime.now())
);
dateTime = dateTime.withHour(8).withMinute(0).withSecond(0).withNano(0);

if (isDAYILY()) {
    dateTime = dateTime.minusDays(1);
} else if (isWEEK()) {
    dateTime = dateTime.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
} else if (isMONTH()) {
    dateTime = dateTime.withDayOfMonth(1);
}

long dataTime = dateTime.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli();
```

---

### 6.3 dataDate 與 dataTime 的關係

`dataDate`（`DATETIME`）與 `dataTime`（`BIGINT` 毫秒）**儲存的是完全相同的時刻**，只是格式不同：

| 欄位 | 值範例 | 格式 | 用途 |
|------|--------|------|------|
| `dataTime` | `1713394800000` | epoch 毫秒 | 複合 PK、排序、Highcharts X 軸 |
| `dataDate` | `2024-04-18 08:00:00` | DATETIME | SQL 日期函式查詢（`EXTRACT`, `BETWEEN`）|

寫入時兩個欄位必須同步，用同一個 `LocalDateTime` 物件賦值：

```java
stockData.setDataDate(dateTime);   // LocalDateTime
id.setDataTime(dateTime.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli());
```

---

### 6.4 Python 標準轉換函式

新增爬蟲或資料寫入時，使用以下函式確保格式一致：

```python
from datetime import datetime, time
import pytz

TZ_TAIPEI = pytz.timezone("Asia/Taipei")

def to_stock_datatime(date: datetime, data_type: str) -> tuple[int, datetime]:
    """
    將任意日期轉換為 StockData 的標準 dataTime（毫秒）與 dataDate（datetime）。

    Args:
        date:      原始日期（任意時刻皆可，函式內部會正規化）
        data_type: "D"（日線）/ "W"（週線）/ "M"（月線）

    Returns:
        (data_time_ms, data_date)
        - data_time_ms: int，存入 DB 的 BIGINT 毫秒值
        - data_date:    datetime，存入 DB 的 DATETIME 值（帶 tzinfo）
    """
    if date.tzinfo is None:
        date = TZ_TAIPEI.localize(date)
    else:
        date = date.astimezone(TZ_TAIPEI)

    if data_type == "D":
        base = date.replace(hour=8, minute=0, second=0, microsecond=0)
        base = base - __import__("datetime").timedelta(days=1)
    elif data_type == "W":
        days_to_monday = date.weekday()  # Monday=0
        base = date - __import__("datetime").timedelta(days=days_to_monday)
        base = base.replace(hour=8, minute=0, second=0, microsecond=0)
    elif data_type == "M":
        base = date.replace(day=1, hour=8, minute=0, second=0, microsecond=0)
    else:
        raise ValueError(f"不支援的 dataType: {data_type}，合法值為 D / W / M")

    data_time_ms = int(base.timestamp() * 1000)
    return data_time_ms, base


# ── 使用範例 ──────────────────────────────────────────────
# 日線：2024-04-18 的資料
ms, dt = to_stock_datatime(datetime(2024, 4, 19), "D")
# → ms  = 1713394800000（2024-04-18 08:00:00 台灣時間）
# → dt  = 2024-04-18 08:00:00+08:00

# 週線：2024-04-15（週一）~ 2024-04-19（週五）這週
ms, dt = to_stock_datatime(datetime(2024, 4, 17), "W")
# → ms  = 1713139200000（2024-04-15 08:00:00 台灣時間）
# → dt  = 2024-04-15 08:00:00+08:00

# 月線：2024 年 4 月
ms, dt = to_stock_datatime(datetime(2024, 4, 25), "M")
# → ms  = 1711929600000（2024-04-01 08:00:00 台灣時間）
# → dt  = 2024-04-01 08:00:00+08:00
```

---

### 6.5 特別注意事項

1. **dataTime 單位是毫秒，不是秒**：Python `datetime.timestamp()` 回傳秒，必須乘以 1000 才能存入 DB。

2. **日線需要 -1 天（僅 histock 來源）**：histock 的 timestamp 指向收盤隔天；yfinance 直接給收盤日，不需要減。

3. **不可用當下時間直接換算**：必須先正規化到 08:00，再依週期計算基準日，最後才轉毫秒。流程不可顛倒。

4. **Highcharts 直接使用 dataTime**：前端不做任何轉換，拿到毫秒直接當 X 軸時間點。若 dataTime 不符合規範（如時刻不是 08:00），圖表時間軸會出現偏移。

5. **複合 PK 唯一性**：同一支股票（stockId）+ 同一週期（dataType）+ 同一基準日（dataTime）只能有一筆。寫入前先確認 dataTime 計算正確，否則會覆蓋舊資料或產生 PK 衝突。

---

## 七、目前開放的功能清單

### 前端啟用中（`stockapp.js:29-44`）

| 順序 | 頁面 | 功能名稱 |
|------|------|---------|
| 1 | `future-bbi.html` | 多空趨勢（期貨 BBI 分析） |
| 2 | `stock-data.html` | K 線資料（日/週/月 K 線圖）|
| 3 | `stock-gate.html` | 多空戰 K（轉折資料 + 9 條軸線） |
| 4 | `stock-turn.html` | 多空中軸（轉折 K 線圖） |
| 5 | `stock-exclude.html` | 除權息查詢 |

### 前端已關閉（程式碼已存在但被註解）

| 頁面 | 功能 | 備註 |
|------|------|------|
| `stock-gate2.html` | 簡易多空轉折查詢 | 已有頁面檔案 |
| `options-open.html` | 外資選擇權均價資料 | 已有頁面檔案 |
| `feture-open.html` | 外資特定人未沖銷資料 | 已有頁面檔案（注意拼字：feture 非 future） |
| `options-open2.html` | 五大十大未沖銷資料 | 已有頁面檔案 |
| `find-stocks-long.html` | 季線多空查詢 | 已有頁面檔案 |
| `find-stocks-rsi.html` | RSI 多空查詢 | 已有頁面檔案 |
| `stock-buysell2.html` | 買賣點 | 已有頁面檔案 |
| `stock-oldwang.html` | 老王多空 | 已有頁面檔案 |
| `find-stocks-gap.html` | 跳空分析 | 已有頁面檔案 |
| `stock-buysell3.html` | 多排、空排、金叉、死叉 | 已有頁面檔案 |

---

## 八、API 端點清單

### StockController (`/stock`)

| Method | Path | 參數 | 說明 |
|--------|------|------|------|
| GET | `/stock/findStocks` | — | 取得所有股票清單（JSONP） |
| GET | `/stock/findStockInfo` | `si` | 取得單支股票資訊 |
| GET | `/stock/queryStockData` | `si`, `dt`, `dn` | 查詢 K 線 + MA + EMA 資料 |
| GET | `/stock/queryStockData2` | `si`, `dt`, `dn` | Excel 格式輸出（蘭姐用） |
| GET | `/stock/queryStockTurnData` | `si`, `dt`, `dn`, `up?`, `gw?` | 多空戰 K 轉折資料 |
| GET | `/stock/queryStockTurnDataK` | `si`, `dt`, `dn?=20` | 轉折 K 線圖資料 |
| GET | `/stock/queryStockFinanData` | `si`, `c` (year) | 財務資料 |
| GET | `/stock/updateStockData` | `dc?=100`, `si?`, `dataType?`, `startid?`, `endid?` | 更新股票資料（非同步） |
| GET | `/stock/findStockBuySell2` | `dt` | 買賣點查詢 |
| GET | `/stock/findStockBuySell3` | `dt` | 多欄位買賣點查詢 |
| GET | `/stock/findStockByEps` | — | EPS 篩選 |
| GET | `/stock/findStockByOldWang` | `dataType` | 老王多空查詢 |
| GET | `/stock/findStockLongTerm` | — | 長期股查詢 |
| GET | `/stock/findStockRSI` | — | RSI 查詢 |
| GET | `/stock/findStockWithGap` | — | 跳空查詢 |

**排程：** 每週六 13:10 自動執行 `syncStockData()`（`@Scheduled(cron = "0 10 13 ? * 6")`）

### StockExcludeController (`/stockExclude`)

| Method | Path | 參數 | 說明 |
|--------|------|------|------|
| GET | `/stockExclude/queryExcludeData` | — | 除權息查詢（JSONP, callback=jsonp） |
| GET | `/stockExclude/updateStockExclude` | — | 更新除權息資料 |

**排程：** 每天 20:30 自動執行（`@Scheduled(cron = "0 30 20 * * ?")`）

### Futures / Options Controller

| Method | Path | 參數 | 說明 |
|--------|------|------|------|
| GET | `/futures/querybbi` | `type`, `contractMonthType` | 期貨 BBI 趨勢查詢 |
| GET | `/futures/queryFuturesOpen2` | `dc` | 期貨未平倉（Top5/10） |
| GET | `/options/optionsopen` | `dc` | 選擇權未平倉 |
| GET | `/options/optionsopen2` | `dc` | 選擇權五大十大未沖銷 |

### InitData Controller（管理員操作）

| Path | 說明 |
|------|------|
| `/initData/initStockData0000` | 初始化股票主資料 |
| `/initData/initStockDataMA` | 初始化 MA 資料 |
| `/initData/initStockDataRSI` | 初始化 RSI 資料 |
| `/initData/initStockDataTurn` | 初始化轉折資料 |
| `/initData/initStockPriceLevel` | 初始化價格水準 |
| `/initData/initCashFlow` | 初始化現金流 |
| `/initData/initBalanceSheet` | 初始化資產負債表 |
| `/initData/initFinanRatio` | 初始化財務比率 |
| `/initData/initFinanRatio2` | 初始化財務比率（第二版） |
| `/initData/initFinanAll` | 初始化所有財務資料 |
| `/initData/initFutureOpen2` | 初始化期貨未平倉 |
| `/initData/initOptionOpen` | 初始化選擇權未平倉 |
| `/initData/initOptionOpen2` | 初始化選擇權五大十大 |
| `/initData/initStockGaps` | 初始化跳空資料 |

### StockWantsController (`/stockwants`)

| Method | Path | 參數 | 說明 |
|--------|------|------|------|
| GET | `/stockwants/queryStockwants` | — | 查詢權證資料 |
| GET | `/stockwants/importWarrant` | — | 從 CSV 匯入權證資料 |

---

## 九、已知問題與待改善項目

### 已確認的 Bug / 不一致

1. **年線（dataType="Y"）半實作：** HTML 頁面有 `dt=Y` 的選項，但 `StockDataTypeType.java` Enum 中無 `YEAR` 定義，`find()` 會 fallback 到 `UNKNOWN` 再 fallback 到 `DAYILY`，導致年線查詢實際回傳日線資料。
2. **StockDataTurn value 跳號：** value13 和 value28 欄位不存在（從 12 跳 14，從 27 跳 29），加新欄位時請勿假設連號。
3. **MySQL Dialect 版本：** `application.yml` 使用 `MySQL55Dialect`，需確認實際 MySQL 版本是否相符，建議升級至對應版本的 Dialect。
4. **前端頁面命名錯字：** `feture-open.html`（應為 `future-open.html`），但 `stockapp.js` 中的連結使用的是錯字版本，兩者保持一致即可，不要單獨修正一邊。
5. **StockDataMA 欄位混用：** MA 同時包含傳統均線（ma20, ma60）和 Fibonacci 數列（ma21, ma55, ma89, ma144, ma233），造成部分語意重疊（ma20 vs ma21）。

### 架構層面待改善

1. **無 market 欄位：** 主業務 entity 無法區分不同市場的同代碼股票，是加入日股的最大阻礙。
2. **hardcoded API URL：** `stockapp.js:6` 直接寫死 `https://stock.bignoodle.net/StockServer/...`，環境切換需手動修改。
3. **JSONP 安全性：** 整個 API 層使用 JSONP 而非 CORS，不適合未來的 Web 版前端。
4. **密碼明文：** `application.yml` 中的 DB 密碼為明文，應改用環境變數或 Spring Vault。
5. **無 API 版本控管：** 所有端點無版本前綴（如 `/v1/stock/...`），日後 breaking change 困難。

---

## 十、DB 調整計劃

### 加入 market 欄位（加入日股的前置作業）

#### Phase 1：Stocks 表加 market

```sql
ALTER TABLE Stocks ADD COLUMN market VARCHAR(5) NOT NULL DEFAULT 'TW' AFTER stockId;
ALTER TABLE Stocks DROP PRIMARY KEY;
ALTER TABLE Stocks ADD PRIMARY KEY (stockId, market);
CREATE INDEX idx_stocks_market ON Stocks(market);
```

#### Phase 2：StockDataId Composite PK 加 market

> **影響範圍：** StockData、StockDataMA、StockDataEMA、StockDataTurn、StockDataRSI 全部 table

```sql
ALTER TABLE StockData ADD COLUMN market VARCHAR(5) NOT NULL DEFAULT 'TW' AFTER stockId;
ALTER TABLE StockData DROP PRIMARY KEY;
ALTER TABLE StockData ADD PRIMARY KEY (stockId, market, dataType, dataTime);
-- 同步執行 StockDataMA、StockDataEMA、StockDataTurn、StockDataRSI（相同操作）
```

#### Phase 3：StockExclude 加 market

```sql
ALTER TABLE StockExclude ADD COLUMN market VARCHAR(5) NOT NULL DEFAULT 'TW' AFTER stockId;
ALTER TABLE StockExclude DROP PRIMARY KEY;
ALTER TABLE StockExclude ADD PRIMARY KEY (stockId, market, excludeDate);
```

### Java Entity 同步修改清單

| 檔案 | 修改內容 |
|------|---------|
| `entity/StockDataId.java` | 新增 `market` 欄位、setter/getter |
| `entity/StockDataTurn.java` | 複合 PK 使用 StockDataId，隨 StockDataId 自動更新 |
| `entity/StockDataMA.java` | 同上 |
| `entity/StockDataEMA.java` | 同上 |
| `entity/StockDataRSI.java` | 同上 |
| `entity/Stocks.java` | 新增 `market` 欄位；若升為 PK 的一部分則需加 `@EmbeddedId` |
| `entity/StockExclude.java` | `StockExcludeId` 新增 market |
| `entity/StockExcludeId.java` | 新增 `market` 欄位 |
| `repo/StockDataRepository.java` | 所有 JPQL 查詢加上 `AND id.market = :market` |
| `service/StockService.java` | 查詢方法加 market 參數 |
| `service/StockCommonService.java` | updateStockData 依 market 路由不同資料源 |
| `controller/StockController.java` | API 新增 `market` 參數（可向下相容，預設 `"TW"`） |
| `StockApp/assets/www/js/stockapp.js` | 股票下拉選單依 market 分組顯示 |

### Index 建議

```sql
CREATE INDEX idx_stockdata_stock_type ON StockData(stockId, dataType);
CREATE INDEX idx_stockdata_market ON StockData(market);
CREATE INDEX idx_stocks_status ON Stocks(status);
CREATE INDEX idx_stockexclude_date ON StockExclude(excludeDate DESC);
```

---

## 十一、開發規範

### 命名規則

| 項目 | 規則 | 範例 |
|------|------|------|
| Entity 類別 | UpperCamelCase，對應 DB table 名稱 | `StockData`, `StockDataMA` |
| DB table 名稱 | UpperCamelCase（現行慣例） | `StockData`, `Stocks` |
| DB 欄位名稱 | lowerCamelCase | `stockId`, `dataType`, `dataTime` |
| API 路徑 | lowerCamelCase | `/queryStockTurnData` |
| API 參數 | 縮寫小寫（現行慣例） | `si`=stockId, `dt`=dataType, `dn`=dataCount |
| 前端 JS function | lowerCamelCase | `initIds()`, `crateHighchart()` |

### API 參數縮寫對照表

| 縮寫 | 完整名稱 | 說明 |
|------|---------|------|
| `si` | stockId | 股票代碼 |
| `dt` | dataType | 資料週期（D/W/M） |
| `dn` | dataCount / dataNumber | 資料筆數 |
| `up` | updateData | 是否更新資料（1=是） |
| `gw` | getWants | 是否取得權證（1=是） |
| `dc` | dataCount | 資料筆數（期貨/選擇權用） |
| `c` | year | 年份（財務資料用） |

### 重要注意事項

1. **時間戳基準：** `dataTime` 欄位為 Long，所有時間以台灣早上 08:00（`Asia/Taipei`）為基準，加入日股後需特別注意時區處理（日本 UTC+9 = 台灣相同時區，但交易時段不同）。
2. **Composite PK 不可隨意改動：** `StockDataId` 是多個 table 的共同 PK，任何結構變動會連動 `StockData`、`StockDataMA`、`StockDataEMA`、`StockDataTurn`、`StockDataRSI`。
3. **JSONP Callback 名稱：** 多數 API 使用 `callback=call`，除權息使用 `callback=jsonp`，財務資料使用 `callback=call2`，不可隨意更改。
4. **`@Transient` 欄位：** `StockExclude.futures` 和 `StockExclude.wants` 不寫入 DB，由程式邏輯動態填入，勿誤加 `@Column`。
5. **非同步更新：** `updateStockData` 和資料更新系列 API 為非同步（`@Async`），呼叫後立即回傳，實際完成時間不定。

### 前端圖表顏色規範

**MA/EMA 線條顏色（`stockapp.js:72-84`）：**

| 期數 | 顏色 |
|------|------|
| 3, 5, 34, 89 | `#FF8000`（橘） |
| 7, 8 | `#FFFFFF`（白） |
| 10 | `#EA20EA`（洋紅） |
| 13 | `#0000FF`（藍） |
| 21 | `#FFFF00`（黃） |
| 53, 55 | `#00FF00`（綠） |
| 144 | `#FF0080`（粉紅） |
| 233 | `#8000FF`（紫） |

**K 線顏色（`stockapp.js:143-148`）：**
- 下跌 K 線：`#005500`（深綠，台股慣例）
- 上漲 K 線：`#D52B00`（深紅，台股慣例）

**多空轉折 9 軸顏色（`stock-gate.html`）：**

| level | 名稱 | 顏色 |
|-------|------|------|
| level1 | 買耗 | `#058DC7` |
| level2 | 軋空 | `#873322` |
| level3 | 轉強 | `#FFF263` |
| level4 | 中軸 | `#FF9655` |
| level5 | 回撐 | `#64E572` |
| level6 | 轉弱 | `#4f9ab7` |
| level7 | 殺多 | `#CC7700` |
| level8 | 賣耗 | `#ED561B` |
| level9 | 多空線 | `#50B432` |

---

## 十二、專案重要檔案路徑速查

### 後端

```
stockserver/src/main/java/com/stockapp/stockserver/
├── StockserverApplication.java              # Spring Boot 入口
├── controller/
│   ├── AbstractController.java             # Controller 基底類別
│   ├── StockController.java                # 主要股票 API（含排程）
│   ├── StockExcludeController.java         # 除權息 API（含排程）
│   ├── StockWantsController.java           # 權證 API
│   └── WebhookController.java              # Webhook API
├── service/
│   ├── StockService.java                   # 主要股票商業邏輯
│   ├── StockCommonService.java             # 共用邏輯（非同步更新）
│   ├── StockDataTurnService.java           # 轉折計算邏輯
│   ├── StockExcludeService.java            # 除權息邏輯
│   ├── BizDateService.java                 # 交易日計算
│   └── WebhookService.java                 # Webhook 邏輯
├── entity/
│   ├── StockDataId.java                    # 複合 PK（stockId + dataType + dataTime）
│   ├── Stocks.java                          # 股票主檔
│   ├── StockData.java                       # K 線資料
│   ├── StockDataMA.java                     # SMA 均線
│   ├── StockDataEMA.java                    # EMA 均線
│   └── StockDataTurn.java                   # 多空轉折指標
├── enums/
│   └── StockDataTypeType.java              # dataType Enum（D/W/M/UNKNOWN）
└── resources/
    └── application.yml                     # DB 連線、Port、Log 設定
```

### 前端

```
StockApp/assets/www/
├── js/
│   └── stockapp.js                         # 共用 JS（選單、下拉、圖表函式）
├── stock-data.html                         # K 線圖頁面（啟用中）
├── stock-gate.html                         # 多空戰 K 頁面（啟用中）
├── stock-turn.html                         # 多空中軸頁面（啟用中）
├── stock-exclude.html                      # 除權息查詢頁面（啟用中）
├── future-bbi.html                         # 多空趨勢頁面（啟用中）
└── stock-update.html                       # 管理員資料更新頁面
```

### Python 爬蟲

```
python/
├── collector/
│   └── fetch_nikkei225.py                  # 日股日線爬蟲（連線資訊從 ../.env 讀取）
└── .env                                    # DB 連線資訊（不進 Git）
```

---

## 十三、MySQL 5.5 分區限制

### 分區函式的嚴格限制

MySQL 5.5 的 `PARTITION BY RANGE` 分區函式有以下限制：

| 情況 | 支援 |
|------|------|
| 整數欄位直接分區 | ✅ `PARTITION BY RANGE (dataYear)` |
| 簡單整數四則運算 | ✅ `PARTITION BY RANGE (dataTime / 1000)` |
| 函式運算結果 | ❌ `PARTITION BY RANGE (YEAR(FROM_UNIXTIME(dataTime/1000)))` |
| 複合函式 | ❌ `PARTITION BY RANGE (YEAR(dataDate))` on DATETIME |

> **注意：** `YEAR(datetime_col)` 在 MySQL 5.5 中**亦不支援**作為分區函式，只有純整數欄位或簡單整數運算才合法。

### 解決方案：新增 `dataYear` 欄位

若資料表只有 `dataTime`（BIGINT 毫秒）而無 `dataDate`（DATETIME）欄位，需新增整數欄位存放年份作為分區鍵：

```sql
-- 建表時新增 dataYear
dataYear INT NOT NULL,   -- 存放 YEAR(FROM_UNIXTIME(dataTime/1000))

PRIMARY KEY (stockId, dataType, dataTime, dataYear),  -- 分區鍵必須在 PK 內
PARTITION BY RANGE (dataYear) (
    PARTITION p2020 VALUES LESS THAN (2021),
    ...
)

-- INSERT 時計算填入
INSERT INTO table (stockId, dataType, dataTime, dataYear, ...)
SELECT stockId, dataType, dataTime, YEAR(FROM_UNIXTIME(dataTime/1000)), ...
FROM source_table;
```

### 適用範圍

| 表 | dataDate 欄位 | 分區方式 |
|----|--------------|---------|
| `stockdataturn_new` | ❌ 無 | 需新增 `dataYear INT`，`PARTITION BY RANGE (dataYear)` |
| `StockData`、`StockDataMA` 等 | ✅ 有 | 可直接用 `dataDate`（但 DATETIME 在 5.5 仍有限制，建議同樣新增整數欄位） |

---

## 十四、Docker 環境資訊

| Container 名稱 | 用途 | Port |
|---------------|------|------|
| `stock` | Tomcat 10.1，Spring Boot + HTML5 前端 | 8101→8080 |
| `stock-analysis` | Python Dash 儀表板 | 8050 |
| `stock-jp-api` | Python FastAPI 日股資料服務 | 8090 |
| `mysql55` | MySQL 5.5，主資料庫 stockapp | 3305→3306 |

### Spring Boot 部署方式

- 程式碼位置：`stock-analysis/stockserver/`
- 編譯後 `.class` 直接複製到 Tomcat volume：
  `/mnt/d/Environments/tomcat/10.1/webapps/StockServer/WEB-INF/classes/`
- 部署指令：`bash deploy.sh`（在 `stock-analysis/` 根目錄執行）
- Tomcat 無 manager app，部署後需 `docker restart stock` 使新 class 生效
- Spring Boot API base URL（container 內部）：`http://stock:8080/StockServer/`
- 對外 URL：`https://stock.bignoodle.net/StockServer/`

### jp-fetcher 設定（`application.yml`）

- `jp-fetcher.url`：`http://stock-jp-api:8090/api/fetch/jp`
- Fundamental URL：`http://stock-jp-api:8090/api/fetch/fundamental`（由 `JpDataFetchService.java:61` 動態 replace 產生，非獨立設定項）

---

## 十五、stock_page_raw 資料收集

用途：收集網頁原始 HTML，供 n8n + AI 後續處理。

### API

| Method | Path | 說明 |
|--------|------|------|
| POST | `/stockPageRaw/save` | 儲存網頁原始 HTML |

**POST `/stockPageRaw/save` 參數：**

| 參數 | 必填 | 說明 |
|------|------|------|
| `url` | ✅ | 來源網址 |
| `htmlContent` | ✅ | 網頁原始 HTML |
| `source` | ❌ | 資料來源識別（如 `"histock"`, `"yahoo"`） |

### ai_status 狀態碼

| 值 | 意義 |
|----|------|
| `0` | 待處理 |
| `1` | 處理中 |
| `2` | 完成 |
| `3` | 失敗 |

### 相關檔案

```
stockserver/src/main/java/com/stockapp/stockserver/
├── entity/StockPageRaw.java
├── repo/StockPageRawRepository.java
└── controller/StockPageRawController.java
```

---

## 十六、youtube_transcribe_queue 逐字稿佇列

用途：收集 YouTube URL，供 n8n 自動下載逐字稿。

### API

| Method | Path | 說明 |
|--------|------|------|
| POST | `/youtubeTranscribe/save` | 新增 URL，寫入佇列（ai_status=0） |
| GET | `/youtubeTranscribe/unprocessed` | 查詢待處理記錄（ai_status=0，LIMIT 3，依 created_at ASC） |
| PUT | `/youtubeTranscribe/updateStatus` | 更新處理狀態（ai_status、ai_result、updated_at） |

### ai_status 狀態碼

| 值 | 意義 |
|----|------|
| `0` | 待處理 |
| `1` | 處理中 |
| `2` | 完成 |
| `3` | 失敗 |

### 相關檔案

```
stockserver/src/main/java/com/stockapp/stockserver/
├── entity/YoutubeTranscribeQueue.java
├── repo/YoutubeTranscribeQueueRepository.java
└── controller/YoutubeTranscribeController.java
```

---

## 十七、資料收集工具頁面

### 統一工具頁面：savepage.html

**位置（兩個都要同步修改）：**
1. `StockApp/www/savepage.html`（Git 版本，原始碼管理）
2. `/mnt/d/Environments/tomcat/10.1/webapps/ROOT/savepage.html`（Tomcat 對外提供）

**同步指令：**
```bash
cp /mnt/d/Environments/stock-analysis/stock-analysis/StockApp/www/savepage.html \
   /mnt/d/Environments/tomcat/10.1/webapps/ROOT/savepage.html
```

### 規範

- 所有新增的資料收集 API，都要在此頁面新增對應的 Tab
- 每次修改都要同步更新兩個位置（先改 StockApp/www，再 cp 到 ROOT）
- test-tools.html 已廢棄，功能已統一移至 savepage.html

### 目前已有的 Tab

| Tab 名稱 | API 端點 | 說明 |
|----------|---------|------|
| 📄 儲存網頁 | `POST /stockPageRaw/save` | 收集網頁原始 HTML，供 n8n + AI 處理 |
| ▶️ YouTube 逐字稿 | `POST /youtubeTranscribe/save` | 新增 YouTube URL 至逐字稿佇列 |

### 新增 Tab 時的注意事項

1. 在 `savepage.html` 的 Tab 導覽列加入新 Tab 按鈕
2. 在 `savepage.html` 的 Tab 內容區加入對應的表單
3. 確認 API 端點（`https://stock.bignoodle.net/StockServer/` 開頭）
4. 執行同步指令更新 Tomcat 版本

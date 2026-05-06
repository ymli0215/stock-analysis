# SYSTEM_OVERVIEW.md — StockApp 系統功能說明文件

> 本文件面向開發者，快速了解系統的使用者功能、資料流程與擴充限制。
> 最後更新：2026-04-18

---

## 一、系統定位

StockApp 是一套**台灣股票技術分析工具**，採用私有伺服器架構：

- 後端定期從外部資料源（histock.tw）抓取台股 K 線資料並存入 MySQL
- 前端（以 Cordova/PhoneGap 封裝的 App）透過 JSONP 呼叫後端 API，呈現技術分析圖表
- 目前沒有使用者登入系統（前端無帳號機制），所有人共用同一資料集
- 主要使用者：少數特定人員（非公開服務）

---

## 二、系統功能清單（使用者角度）

### 目前開放的功能

| # | 功能名稱 | 說明 | 對應頁面 |
|---|---------|------|---------|
| 1 | **多空趨勢** | 台指期貨 BBI（多空均線）走勢分析，區分多空方向 | future-bbi.html |
| 2 | **K 線資料** | 個股日/週/月 K 線圖，含 MA（移動平均）和 EMA（指數平均）多條均線疊加 | stock-data.html |
| 3 | **多空戰 K** | 個股 K 線圖疊加 9 條多空轉折軸線（買耗/軋空/轉強/中軸/回撐/轉弱/殺多/賣耗/多空線），用於判斷多空強弱 | stock-gate.html |
| 4 | **多空中軸** | 個股多空轉折 K 線圖，以轉折計算值取代傳統 OHLC，觀察壓力支撐軸變化 | stock-turn.html |
| 5 | **除權息查詢** | 查詢各股除權息歷史紀錄，含配息、配股、殖利率、除後參考價等資訊 | stock-exclude.html |

### 系統內建但前端未開放的功能（後端已實作）

| 功能 | 說明 |
|------|------|
| 外資選擇權均價資料 | 買/賣權未沖銷部位統計 |
| 外資特定人期貨未沖銷 | 期貨 Top5/Top10 未平倉分析 |
| RSI 多空篩選 | 以 RSI 技術指標篩選股票 |
| 季線多空查詢 | 依季線多空位置篩選股票清單 |
| 跳空分析 | 掃描有跳空缺口的股票 |
| 買賣點分析 | 多空買賣訊號整合分析 |
| 老王多空 | 老王多空策略篩選 |
| 金叉/死叉掃描 | 均線黃金交叉/死亡交叉篩選 |
| 財務分析 | 現金流、資產負債表、財務比率查詢 |
| 權證資料 | 個股對應權證資訊 |

---

## 三、各頁面功能說明

### 3.1 future-bbi.html — 多空趨勢

**使用者操作流程：**
1. 選擇合約月份類型（近月 / 遠月）
2. 選擇多方或空方
3. 頁面顯示台指期貨的 BBI（多空均線）趨勢圖

**資料來源：** `FuturesOpen` table  
**API：** `GET /futures/querybbi?type={1|2}&contractMonthType={1|2}`  
**特性：** 此頁為期貨大盤分析，不需選擇個股

---

### 3.2 stock-data.html — K 線資料

**使用者操作流程：**
1. 從下拉選單選擇股票代碼（`findStocks` API 動態載入）
2. 選擇時間週期：日（D）/ 週（W）/ 月（M）
3. 輸入要查詢的筆數（預設值因頁面而異）
4. 點擊查詢，右方顯示 Highcharts StockChart 圖表

**圖表內容：**
- 主圖：蠟燭圖（開高低收）
- 疊加：SMA 均線（3, 5, 10, 21, 55, 144, 233 期）
- 疊加：EMA 均線（部分期數）
- 副圖：成交量柱狀圖

**資料來源：** `StockData` + `StockDataMA` + `StockDataEMA` tables  
**API：** `GET /stock/queryStockData?si={stockId}&dt={D|W|M}&dn={count}`

---

### 3.3 stock-gate.html — 多空戰 K

**使用者操作流程：**
1. 選擇股票
2. 選擇週期（D/W/M）
3. 輸入筆數
4. 圖表顯示 K 線 + 9 條多空軸線

**圖表內容（9 軸線含義）：**

| 軸線 | 名稱 | 多空意義 |
|------|------|---------|
| level1 | 買耗（藍） | 多方耗盡訊號 |
| level2 | 軋空（棕） | 空方被軋回補 |
| level3 | 轉強（黃） | 行情轉強確認 |
| level4 | 中軸（橘） | 多空分水嶺 |
| level5 | 回撐（綠） | 回檔支撐 |
| level6 | 轉弱（青） | 行情轉弱確認 |
| level7 | 殺多（深橘） | 多方被殺出場 |
| level8 | 賣耗（紅） | 空方耗盡訊號 |
| level9 | 多空線（亮綠） | 趨勢判斷基準線 |

**資料來源：** `StockDataTurn` table（`middle` + `value1`~`value31`）  
**API：** `GET /stock/queryStockTurnData?si={id}&dt={D|W|M}&dn={count}`  
**特殊說明：** 此 API 會預測隔天的轉折值（回傳資料多一筆「明天」），最舊 3 筆 StockData 會被忽略。

---

### 3.4 stock-turn.html — 多空中軸

**使用者操作流程：**
1. 選擇股票
2. 選擇週期（D/W/M）
3. 輸入筆數
4. 顯示以轉折值計算的特殊 K 線圖

**與多空戰 K 的差異：**
- 多空戰 K（stock-gate）：原始 K 線 + 轉折軸線疊加
- 多空中軸（stock-turn）：轉折值本身作為 K 線數值，呈現「轉折 K 線」

**資料來源：** `StockDataTurn` table  
**API：** `GET /stock/queryStockTurnDataK?si={id}&dt={D|W|M}&dn=20`

---

### 3.5 stock-exclude.html — 除權息查詢

**使用者操作流程：**
1. 頁面載入時自動查詢所有除權息紀錄
2. 顯示表格：股票代碼、名稱、除息日、配息、配股、殖利率、除後參考價等
3. 支援排序、篩選

**資料來源：** `StockExclude` table  
**API：** `GET /stockExclude/queryExcludeData`（JSONP, callback=jsonp）  
**自動更新：** 每天 20:30 後端自動從外部資料源同步最新除權息資料

---

### 3.6 stock-update.html — 資料更新管理（管理員用）

**功能：** 手動觸發各項資料的初始化與更新  
**使用者：** 系統管理員  
**提供的操作按鈕：**
- 初始化股票主資料（`initStockData0000`）
- 初始化 MA / RSI / 轉折資料
- 初始化財務資料（現金流、資產負債表、財務比率）
- 初始化期貨/選擇權資料
- 初始化跳空資料

---

## 四、資料流程

### 4.1 K 線資料更新流程

```
[外部資料源: histock.tw]
        |
        | HTTP 爬蟲 / API 呼叫
        v
[stockserver: StockCommonService.updateStockData()]  ← @Scheduled 每週六 13:10
        |
        | 計算 MA、EMA、RSI、轉折指標
        v
[MySQL DB]
  ├── StockData（OHLCV）
  ├── StockDataMA（SMA 均線）
  ├── StockDataEMA（EMA 均線）
  ├── StockDataRSI（RSI 指標）
  └── StockDataTurn（多空轉折 31 個值）
        |
        | JSONP API 呼叫
        v
[StockApp 前端]
        |
        | Highcharts StockChart 渲染
        v
[使用者畫面]
```

### 4.2 除權息資料更新流程

```
[外部除權息資料源]
        |
        | @Scheduled 每天 20:30
        v
[StockExcludeService.updateStockExclude()]
        |
        | 計算殖利率、除後參考價、黃金比例
        v
[MySQL: StockExclude table]
        |
        | GET /stockExclude/queryExcludeData (JSONP)
        v
[stock-exclude.html 前端表格]
```

### 4.3 使用者查詢 K 線流程

```
使用者選擇 [股票代碼 + 週期 + 筆數]
        |
        | $.ajax JSONP 請求
        v
GET https://stock.bignoodle.net/StockServer/stock/queryStockTurnData
        ?si=2330&dt=D&dn=60&callback=call
        |
        | StockController → StockService
        | 查詢 StockData + StockDataTurn
        v
JSONP 回應（JSON wrapped in callback）
        {
          sname: "台積電",
          sdata: [[timestamp, open, high, low, close, volume], ...],
          level1: [[timestamp, value], ...],
          level2: [[timestamp, value], ...],
          ...（level1~level9）
        }
        |
        | Highcharts 渲染
        v
[蠟燭圖 + 9 條多空軸線疊加]
```

### 4.4 股票下拉選單載入流程

```
任意頁面載入
        |
        | initIds("selectpicker_id") 呼叫
        v
GET /stock/findStocks?callback=call
        |
        v
回傳所有 Stocks（status=1 的持續買賣中股票）
        |
        | 動態加入 <option data-subtext="股票名稱">代碼</option>
        v
$('#id').selectpicker() 初始化 bootstrap-select 可搜尋下拉
```

---

## 五、現有系統限制

### 5.1 市場限制

| 限制 | 說明 |
|------|------|
| **只有台股** | StockData 等主要 table 無 market 欄位，無法區分市場 |
| **部分美股指數** | DJI、NASDAQ、SP500、YM 以特殊 stockId 形式儲存，非完整的美股支援 |
| **無日股** | 未支援日本股市（TSE、JASDAQ 等）|
| **無港股** | 未支援香港恒生市場 |
| **台股代碼衝突風險** | 若要加入日股，部分台日代碼（如 7203）會衝突 |

### 5.2 技術限制

| 限制 | 說明 |
|------|------|
| **JSONP 跨域** | 現有前後端全用 JSONP，不適合非 App 環境（如 Web SPA）|
| **無使用者系統** | 無登入、無權限控制、所有操作共用 |
| **年線未完整實作** | dataType="Y" 在前端有選項，但後端 Enum 未定義，實際回傳日線資料 |
| **排程固定台灣時間** | 後端排程和時間基準寫死 `Asia/Taipei`，加入其他時區市場需重構 |
| **資料源單一** | 所有台股資料依賴 histock.tw，若資料源異動則系統停擺 |
| **無歷史回溯初始化** | 無法一鍵重建某支股票從上市以來的完整歷史資料 |

### 5.3 資料完整性限制

| 限制 | 說明 |
|------|------|
| **StockDataTurn value 跳號** | value13、value28 不存在，欄位不連續 |
| **StockDataMA 欄位混用** | 同時有傳統均線（ma20, ma60）和 Fibonacci 均線（ma21, ma55），部分重疊 |
| **除權息資料為台股特有** | 除權息制度與計算方式各市場不同，日後需要各市場個別實作 |

---

## 六、未來擴充計劃

### 6.1 短期（加入日股支援）

**優先順序：高**

#### 步驟一：DB 設計
1. `Stocks` table 加入 `market` 欄位（VARCHAR(5)，`TW`/`JP`/`US`）
2. `StockDataId` Composite PK 加入 `market`，同步影響 `StockData`、`StockDataMA`、`StockDataEMA`、`StockDataTurn`、`StockDataRSI`
3. 現有台股資料 backfill `market = 'TW'`

#### 步驟二：後端
1. `StockDataTypeType` Enum 補充 `YEAR("Y")` 定義
2. `StockService` 所有查詢加入 market 參數（預設 `"TW"` 保持向下相容）
3. 新增日股資料抓取服務（對接 Yahoo Finance JP 或 JPX 資料）
4. `BizDateService` 支援多市場交易日曆（TSE vs TWSE）

#### 步驟三：前端
1. `stockapp.js` 的 `initIds()` 改為依 market 分組顯示下拉選單
2. 各頁面新增市場切換器（TW / JP / US）
3. 查詢 API 呼叫加入 `market` 參數

#### 注意事項
- 日本股票代碼為 4 位數字（如 `7203` Toyota），與台灣部分股票代碼衝突
- 建議 stockId 在 `market=JP` 時儲存純數字（`7203`），用 market 欄位區分，**不**加 `.T` 後綴
- 日股交易時間：09:00～11:30, 12:30～15:30（JST），與台股不同

### 6.2 中期（前端現代化）

| 項目 | 說明 |
|------|------|
| 改用 CORS + REST | 取代 JSONP，支援標準 Web 應用 |
| API 版本化 | 加入 `/v1/` 前綴，便於未來 breaking change |
| 環境設定外部化 | API URL 改由設定檔管理，不再 hardcoded |
| DB 密碼安全化 | application.yml 密碼改為環境變數 |
| MySQL Dialect 升級 | 從 `MySQL55Dialect` 升級至對應實際版本 |

### 6.3 長期（功能擴充）

| 功能 | 說明 |
|------|------|
| 重新啟用已關閉功能 | 將 stockapp.js 中被註解的功能逐一測試並重新開放 |
| 使用者系統 | 加入登入機制，支援個人化設定（自選股清單等）|
| 年線（dataType=Y）| 補完 Enum 定義和服務層實作 |
| 推播通知 | 整合 Webhook 機制，當股票達到特定轉折條件時推播 |
| 多資料源支援 | 為台股增加備援資料源，降低對 histock.tw 的單點依賴 |

---

## 七、開發環境快速啟動

### 後端

```bash
cd stockserver
# 確認 application.yml DB 連線設定
mvn spring-boot:run
# 服務啟動於 http://localhost:8089/StockServer/
```

### 前端

```
StockApp/ 為 Cordova App 專案目錄
直接用瀏覽器開啟 StockApp/assets/www/stock-data.html 可測試
（API 已設定為 https://stock.bignoodle.net/StockServer/，需伺服器在線）
```

### 資料庫連線資訊

```
Host:     1.34.57.147:3308
Database: stockapp
Driver:   MySQL（須確認版本）
```

---

## 八、常見問題 FAQ

**Q: 為什麼圖表沒有資料？**  
A: 可能原因：① 後端伺服器未啟動 ② 指定股票代碼的資料尚未初始化 ③ JSONP 跨域失敗。先開啟瀏覽器 DevTools 確認 Network 請求狀況。

**Q: 如何新增一支新股票？**  
A: ① 在 `Stocks` table 插入資料 ② 呼叫 `/initData/initStockData0000` 初始化 K 線 ③ 呼叫 `/initData/initStockDataMA` 計算 MA ④ 呼叫 `/initData/initStockDataTurn` 計算轉折

**Q: 為什麼選週線/月線後資料筆數比預期少？**  
A: 正常現象。日線 200 筆約等於 1 年資料；週線 200 筆約等於 4 年；月線 200 筆約等於 16 年。如果資料庫只有近幾年的資料，週/月線的可用筆數自然較少。

**Q: queryStockTurnData 回傳的「明天」是什麼？**  
A: `StockDataTurn` 的轉折值可以預測下一交易日的技術水準，API 故意在回傳清單中多加一筆「未來日期」的轉折值，供使用者參考明日的多空軸線位置。

**Q: 如何確認哪些功能有在使用？**  
A: 參考 `StockApp/assets/www/js/stockapp.js` 的 `resetMenu()` 函式，沒有被 `//` 註解的連結就是目前開放的功能。

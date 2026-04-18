# /stock/queryStockTurnDataK 規格說明書

## 1. 目的
此 API 用於回傳「轉折戰 K 圖」所需資料，包含：
1. OHLC 序列 (`sdata`)
2. 含多空轉折欄位的完整 `TurnKData` 清單 (`charts`)

此 API 會在查詢前觸發資料更新與轉折指標重算（具有副作用）。

---

## 2. 路由與參數
- Controller 路徑：`GET /StockServer/stock/queryStockTurnDataK`
- 對應程式：
  - Controller: `StockController.queryStockTurnDataK`
  - Service: `StockService.queryStockTurnDataK`

### 2.1 Request 參數
- `si` (String, 必填): 股票代碼
- `dt` (String, 必填): 週期代碼 (`D/W/M`)
- `dn` (Integer, 選填，預設 `20`): 取樣筆數

### 2.2 參數正規化
1. 查無 `stockId` 對應股票 -> 回傳 `null`
2. `dt` 無法辨識 -> 改為 `D`

---

## 3. 執行流程（高階）
1. 檢查股票存在
2. 正規化 dataType
3. 先更新股價資料，再更新 Turn 指標
4. 抓取 `StockData`（由新到舊）
5. 將歷史資料轉成 `TurnKData`（缺資料時會補算）
6. 生成未來 3 期推估資料
7. 依時間排序後輸出 `sdata/charts/sname`

---

## 4. 資料更新與依賴
查詢前必做：
1. `stockCommonService.updateStockData(stockId, dataType, dataCount)`
2. `stockCommonService.updateDataTurn(stockId, dataType, dataCount)`

歷史資料逐筆組裝時，若 `StockDataTurn` 不存在或 `middle == null`，會再補做一次 `updateDataTurn`。

---

## 5. 計算規則

### 5.1 歷史資料組裝
- 來源：`StockDataRepository.findDataDesc(stockId, dataType, dataCount)`
- 迴圈範圍：`i = 0` 到 `datas.size() - 7`
- 每筆資料複製：
  - 先從 `StockData` 複製 OHLC
  - 再從 `StockDataTurn` 複製 `middle/value1...value31`

### 5.2 未來 3 期資料生成
先把最新一筆 `StockData` 複製 3 次插入 `datas`，日期依週期前推：
- `D`: `+1,+2,+3` 天
- `W`: `+7,+14,+21` 天
- `M`: `+1,+2,+3` 月

排序 `datas` 後，針對每個未來點計算 `TurnKData`。

### 5.3 指標公式（未來點）
令 `currentIndex = datas.size() - (3 - i)`

1. `value1`（DD 開盤破站不上空）
- 取最近 4 筆 OHLC
- `value1 = round2(sum(OHLC_4筆) / 16)`

2. `value5`（高控多停利）
- `high11 = max(前 11 筆 high)`
- `low11  = min(前 11 筆 low)`
- `value5 = round2((2*high11 + low11) / 3)`

3. `value2`（易多空線）
- `value2 = round2((本筆value5 + 前1筆value5 + 前2筆value5) / 3)`

4. `value3`（超強）
- `sum = Σ(close + close + low)`，區間為最近 20 筆
- `value3 = round2(sum * 1.0618 / 60)`

5. `value4`（乖離短賣）
- 與 `value3` 同一個 `sum`
- `value4 = round2(sum * 1.0874 / 60)`

6. `value6`（低控空回補）
- `value6 = round2((high11 + 2*low11) / 3)`

7. `value7`（嘎空點）與 `value8`（D殺多）
- `high12 = max(前 12 筆 high)`
- `low12  = min(前 12 筆 low)`
- `value7 = round2((high12 - low12)*0.84 + low12)`
- `value8 = round2((high12 - low12)*0.16 + low12)`

8. `middle`（日多空中軸）
- `high_avg6  = avg(最近 6 筆 high)`
- `low_avg13  = avg(最近 13 筆 low)`
- `high_avg26 = avg(最近 26 筆 high)`
- `middle = round2((high_avg6 + low_avg13 + high_avg26) / 3)`

9. middle 衍生關卡
- `value9  = round2(middle*(1-0.0764))`
- `value10 = round2(middle*(1-0.0618))`
- `value11 = round2(middle*(1-0.05))`
- `value29 = round2(middle*(1-0.123))`
- `value30 = round2(middle*(1-0.0989))`
- `value31 = round2(middle*(1-0.088))`
- `value12 = round2(middle*(1-0.0382))`
- `value14 = round2(middle*(1-(0.0236+0.0382)/2))`
- `value15 = round2(middle*(1-0.0236))`
- `value16 = round2(middle*(1-0.0191))`
- `value17 = round2(middle*(1-0.0146))`
- `value19 = round2(middle*(1-0.0087446))`
- `value20 = round2(middle*1.0087446)`
- `value21 = round2(middle*1.01236)`
- `value22 = round2(middle*1.0236)`
- `value23 = round2(middle*(1+(0.0382+0.0236)/2))`
- `value24 = round2(middle*1.0382)`
- `value25 = round2(middle*1.05)`
- `value26 = round2(middle*1.0618)`
- `value27 = round2(middle*1.0764)`

---

## 6. 回傳結構
`Map<String, Object>`
- `sdata`: `[[dataTime, open, high, low, close], ...]`
- `sname`: 股票名稱
- `charts`: `List<TurnKData>`
  - 內含 `middle` 與 `value1...value31`（非連續）

---

## 7. 例外與邊界
1. 任一例外被捕捉後回傳 `null`
2. 程式假設資料量足夠（例如需用到 26 筆回看）；資料不足時有索引風險
3. 此 API 為「查詢 + 更新」混合型，非純讀取

---

## 8. 與實作一致性備註
1. `charts` 在回傳前會以 `TurnKDataComparator` 排序
2. 四捨五入由 `NumberUtils.round(..., "##.00")` 實作，文件以 `round2` 表示
3. 時間戳為 `Asia/Taipei` 時區毫秒值（dataTime）

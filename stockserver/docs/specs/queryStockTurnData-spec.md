# /stock/queryStockTurnData 規格說明書

## 1. 目的
此 API 用於查詢指定股票在指定週期（日/週/月）的多空轉折資料，並回傳：
1. K 線資料序列（`sdata`）
2. 九大關卡（`level1`~`level9`）
3. 轉折價序列（`turns`）
4. 近幾期多空判讀與提示字串（動態 key）

此 API 除查詢外，會觸發資料更新與指標重算（具有副作用）。

---

## 2. 路由與參數
- Controller 路徑：`GET /StockServer/stock/queryStockTurnData`
- 對應程式：
  - Controller: `StockController.queryStockTurnData`
  - Service: `StockService.queryStockTurnData`

### 2.1 Request 參數
- `si` (String, 必填): 股票代碼（例如 `2330`）
- `dt` (String, 必填): 週期代碼
  - `D`: 日線
  - `W`: 週線
  - `M`: 月線
  - 其他值：視為 `D`
- `dn` (Integer, 必填): 目標資料筆數
- `up` (String, 選填，預設 `1`): 目前未實際參與運算
- `gw` (String, 選填，預設 `1`): 目前未實際參與運算

### 2.2 參數正規化
- `stockId` 為空白 -> 直接回傳 `null`
- `dataCount` 為 `null` -> 改為 `150`
- `dt` 無法辨識 -> 改為 `D`

---

## 3. 執行流程（高階）
1. 參數驗證與正規化
2. 先強制更新原始資料 + 技術指標（MA/Level/Turn）
3. 讀取該股票該週期資料（由新到舊）
4. 組裝歷史 `ChartData`
5. 推估未來 3 期 `ChartData`
6. 輸出多組序列資料（`sdata`/`level1~9`/`turns`）
7. 輸出判讀字串與動態 key

---

## 4. 資料更新與依賴
此 API 會呼叫 `forceUpdateStockDatas(stockId, dataType, dataCount)`，內含：
1. `updateStockData`（抓取/同步股價）
2. `updateDataMAHL`（均線）
3. `updateDataLevel`（關卡與轉折價）
4. `updateDataTurn`（轉折戰 K 指標）

說明：即使是查詢，也會寫入 DB。

---

## 5. 主要計算規則
以下規則發生在 `StockService.queryStockTurnData` 的「未來 3 期推估區段」。

### 5.1 取樣與基礎資料
- 查詢筆數：`findCount = max(dataCount, 15)`
- 來源：`StockData`（由新到舊）
- 若沒資料會再強制更新一次後重抓

### 5.2 未來日期推估
基準日：最新一筆 `datas[0].dataDate`
- `D`: `+1, +2, +3` 天
- `W`: `+7, +14, +21` 天
- `M`: `+1, +2, +3` 月

### 5.3 價格欄位（未來 3 期）
未來 3 期的 `open/high/low/close` 皆直接複製最新一期價格。

### 5.4 關卡計算（每個未來點）
令：
- `high1 = max(最近(11-i)筆高點)`
- `low1  = min(最近(11-i)筆低點)`
- `high2 = max(最近(12-i)筆高點)`
- `low2  = min(最近(12-i)筆低點)`
- `box  = high1 - low1`
- `box2 = high2 - low2`

則：
- `level1 = round2(high2 + box2 * 0.2)`  （買耗）
- `level2 = round2(low1  + box  * 0.84)` （軋空）
- `level3 = round2(low1  + box  * 0.666)`（轉強）
- `level4 = round2(low1  + box  * 0.555)`（中軸）
- `level5 = round2(low1  + box  * 0.444)`（回撐）
- `level6 = round2(low1  + box  * 0.333)`（轉弱）
- `level7 = round2(low1  + box  * 0.16)` （殺多）
- `level8 = round2(low2  - box2 * 0.2)`  （賣耗）

`level9`（多空線）：
- 取 `charts` 最後 4 筆（OHLC）
- `level9 = round2((Σ(open+high+low+close))/16)`

### 5.5 轉折價
- `turnPrice = round2(2 * close(三天前) - close(六天前))`
- 實作索引：`2 * datas[2-i].close - datas[5-i].close`

---

## 6. 回傳結構
`Map<String, Object>`，主要 key：
- `sdata`: `[[dataTime, open, high, low, close], ...]`
- `level1` ~ `level9`: 各為 `[[dataTime, value], ...]`
- `turns`: `[[dataTime, turnPrice], ...]`
- `charts`: `List<ChartData>`
- `sname`: 股票名稱
- `close`: 最新 close

### 6.1 動態 key（依 dataType 前綴）
令 `p = dataType`（`D/W/M`）

1. `p + 1` ~ `p + 4`：近 4 個轉折日期（yyyy/MM/dd）
2. `p + 6` ~ `p + 9`：對應轉折價
3. `p + 11` ~ `p + 14`：相對 `close` 的多空字樣（`空` 或 `多`）
4. `p + 5`：turn1（後第2轉折與後第1轉折比較）
5. `p + 10`：turn2（後第3轉折與後第2轉折比較）
6. `p + 15`：`turn1 == turn2 ? 不變 : 轉折`
7. `p + 16`：`(p+13 != p+14) ? 留意轉折 : ""`
8. `p + 17`：文字建議
   - 若 close > 最後三個轉折價：`隔日收盤在{high}之上，則可買進`
   - 若 close < 最後三個轉折價：`隔日收盤跌破{low}，則可放空`
9. `p + 18` / `p + 19`：close 所在關卡區間的上緣/下緣描述
   - 例如：`買耗:xxx`、`軋空:xxx`

---

## 7. 例外與邊界
1. 任一例外被捕捉後：回傳 `null`
2. 內部多處用固定索引運算，若有效資料過少有風險
3. `up`、`gw` 參數目前未參與實際邏輯
4. 此 API 有更新副作用，不是純查詢

---

## 8. 與實作一致性備註
1. 原碼中有部分暫存變數（如 `aver`）計算後未使用
2. 關卡命名與中文註解以程式碼註解為準
3. 四捨五入由 `NumberUtils.round(..., "##.00")` 實作，規格以 `round2` 表示

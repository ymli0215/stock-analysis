-- =============================================================================
-- 10_stockdataema_insert.sql
-- 目的：將 StockDataEMA 資料依年份分批搬入 stockdataema_new（5 年一批）
-- 預期結果：stockdataema_new 的 COUNT(*) 與 StockDataEMA 相等
-- 前置條件：09_stockdataema_create.sql 已成功執行
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 10_stockdataema_insert.sql
-- =============================================================================

USE stockapp;

INSERT INTO stockdataema_new
    (stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233)
SELECT  stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233
FROM    StockDataEMA WHERE dataDate < '2010-01-01';

INSERT INTO stockdataema_new
    (stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233)
SELECT  stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233
FROM    StockDataEMA WHERE dataDate >= '2010-01-01' AND dataDate < '2015-01-01';

INSERT INTO stockdataema_new
    (stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233)
SELECT  stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233
FROM    StockDataEMA WHERE dataDate >= '2015-01-01' AND dataDate < '2020-01-01';

INSERT INTO stockdataema_new
    (stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233)
SELECT  stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233
FROM    StockDataEMA WHERE dataDate >= '2020-01-01' AND dataDate < '2024-01-01';

INSERT INTO stockdataema_new
    (stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233)
SELECT  stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233
FROM    StockDataEMA WHERE dataDate >= '2024-01-01';

-- 驗證：新表筆數應與原表相等
SELECT 'StockDataEMA'     AS tbl, COUNT(*) AS cnt FROM StockDataEMA
UNION ALL
SELECT 'stockdataema_new' AS tbl, COUNT(*) AS cnt FROM stockdataema_new;

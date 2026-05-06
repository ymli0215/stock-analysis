-- =============================================================================
-- 06_stockdatama_insert.sql
-- 目的：將 StockDataMA 資料依年份分批搬入 stockdatama_new（5 年一批）
-- 預期結果：stockdatama_new 的 COUNT(*) 與 StockDataMA 相等
-- 前置條件：05_stockdatama_create.sql 已成功執行
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 06_stockdatama_insert.sql
-- =============================================================================

USE stockapp;

INSERT INTO stockdatama_new
    (stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233)
SELECT  stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233
FROM    StockDataMA WHERE dataDate < '2010-01-01';

INSERT INTO stockdatama_new
    (stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233)
SELECT  stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233
FROM    StockDataMA WHERE dataDate >= '2010-01-01' AND dataDate < '2015-01-01';

INSERT INTO stockdatama_new
    (stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233)
SELECT  stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233
FROM    StockDataMA WHERE dataDate >= '2015-01-01' AND dataDate < '2020-01-01';

INSERT INTO stockdatama_new
    (stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233)
SELECT  stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233
FROM    StockDataMA WHERE dataDate >= '2020-01-01' AND dataDate < '2024-01-01';

INSERT INTO stockdatama_new
    (stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233)
SELECT  stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233
FROM    StockDataMA WHERE dataDate >= '2024-01-01';

-- 驗證：新表筆數應與原表相等
SELECT 'StockDataMA'     AS tbl, COUNT(*) AS cnt FROM StockDataMA
UNION ALL
SELECT 'stockdatama_new' AS tbl, COUNT(*) AS cnt FROM stockdatama_new;

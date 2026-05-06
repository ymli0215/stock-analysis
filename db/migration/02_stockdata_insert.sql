-- =============================================================================
-- 02_stockdata_insert.sql
-- 目的：將 StockData 資料依年份分批搬入 stockdata_new
-- 預期結果：stockdata_new 的 COUNT(*) 與 StockData 相等
-- 前置條件：01_stockdata_create.sql 已成功執行
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 02_stockdata_insert.sql
--
-- 注意：
--   - 依年份分批 INSERT，避免長時間鎖表
--   - 每批完成後可暫停，等系統穩定再繼續下一批
--   - StockData 批次較多（逐年），MA/EMA/Turn 改為 5 年一批
-- =============================================================================

USE stockapp;

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate < '2001-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2001-01-01' AND dataDate < '2002-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2002-01-01' AND dataDate < '2003-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2003-01-01' AND dataDate < '2004-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2004-01-01' AND dataDate < '2005-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2005-01-01' AND dataDate < '2006-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2006-01-01' AND dataDate < '2007-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2007-01-01' AND dataDate < '2008-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2008-01-01' AND dataDate < '2009-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2009-01-01' AND dataDate < '2010-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2010-01-01' AND dataDate < '2011-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2011-01-01' AND dataDate < '2012-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2012-01-01' AND dataDate < '2013-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2013-01-01' AND dataDate < '2014-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2014-01-01' AND dataDate < '2015-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2015-01-01' AND dataDate < '2016-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2016-01-01' AND dataDate < '2017-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2017-01-01' AND dataDate < '2018-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2018-01-01' AND dataDate < '2019-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2019-01-01' AND dataDate < '2020-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2020-01-01' AND dataDate < '2021-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2021-01-01' AND dataDate < '2022-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2022-01-01' AND dataDate < '2023-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2023-01-01' AND dataDate < '2024-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2024-01-01' AND dataDate < '2025-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2025-01-01' AND dataDate < '2026-01-01';

INSERT INTO stockdata_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData WHERE dataDate >= '2026-01-01';

-- 驗證：新表筆數應與原表相等
SELECT 'StockData'     AS tbl, COUNT(*) AS cnt FROM StockData
UNION ALL
SELECT 'stockdata_new' AS tbl, COUNT(*) AS cnt FROM stockdata_new;

-- =============================================================================
-- 18_stockpricelevel_insert.sql
-- 目的：將 StockPriceLevel 資料依年份分批搬入 stockpricelevel_new（5 年一批）
-- 預期結果：stockpricelevel_new 的 COUNT(*) 與 StockPriceLevel 相等
-- 前置條件：17_stockpricelevel_create.sql 已成功執行
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 18_stockpricelevel_insert.sql
-- =============================================================================

USE stockapp;

INSERT INTO stockpricelevel_new
    (stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn)
SELECT  stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn
FROM    StockPriceLevel WHERE dataDate < '2010-01-01';

INSERT INTO stockpricelevel_new
    (stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn)
SELECT  stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn
FROM    StockPriceLevel WHERE dataDate >= '2010-01-01' AND dataDate < '2015-01-01';

INSERT INTO stockpricelevel_new
    (stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn)
SELECT  stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn
FROM    StockPriceLevel WHERE dataDate >= '2015-01-01' AND dataDate < '2020-01-01';

INSERT INTO stockpricelevel_new
    (stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn)
SELECT  stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn
FROM    StockPriceLevel WHERE dataDate >= '2020-01-01' AND dataDate < '2024-01-01';

INSERT INTO stockpricelevel_new
    (stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn)
SELECT  stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn
FROM    StockPriceLevel WHERE dataDate >= '2024-01-01';

-- 驗證：新表筆數應與原表相等
SELECT 'StockPriceLevel'     AS tbl, COUNT(*) AS cnt FROM StockPriceLevel
UNION ALL
SELECT 'stockpricelevel_new' AS tbl, COUNT(*) AS cnt FROM stockpricelevel_new;

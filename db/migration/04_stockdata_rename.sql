-- =============================================================================
-- 04_stockdata_rename.sql
-- 目的：原子 RENAME，將 stockdata_new 上線，原表備份為 stockdata_backup
-- 預期結果：
--   - StockData（分區表）可正常查詢，筆數與備份表相同
--   - stockdata_backup 保留舊資料供回滾使用
-- 前置條件：03_stockdata_verify.sql 確認筆數一致後才可執行
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 04_stockdata_rename.sql
--
-- 回滾方式（若上線後發現問題）：
--   RENAME TABLE StockData TO stockdata_new, stockdata_backup TO StockData;
-- =============================================================================

USE stockapp;

-- 原子操作：停機時間極短
RENAME TABLE
    StockData     TO stockdata_backup,
    stockdata_new TO StockData;

-- 驗證：新表（已分區）與備份表筆數應相同
SELECT 'StockData（新，分區）' AS tbl, COUNT(*) AS cnt FROM StockData
UNION ALL
SELECT 'stockdata_backup（舊）' AS tbl, COUNT(*) AS cnt FROM stockdata_backup;

-- 確認分區已建立
SELECT PARTITION_NAME, PARTITION_DESCRIPTION, TABLE_ROWS
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = 'stockapp' AND TABLE_NAME = 'StockData'
ORDER BY PARTITION_ORDINAL_POSITION;

-- =============================================================================
-- 20_stockpricelevel_rename.sql
-- 目的：原子 RENAME，將 stockpricelevel_new 上線，原表備份為 stockpricelevel_backup
-- 預期結果：
--   - StockPriceLevel（分區表）可正常查詢，筆數與備份表相同
--   - stockpricelevel_backup 保留舊資料供回滾使用
-- 前置條件：19_stockpricelevel_verify.sql 確認筆數一致後才可執行
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 20_stockpricelevel_rename.sql
--
-- 回滾方式：
--   RENAME TABLE StockPriceLevel TO stockpricelevel_new, stockpricelevel_backup TO StockPriceLevel;
-- =============================================================================

USE stockapp;

RENAME TABLE
    StockPriceLevel     TO stockpricelevel_backup,
    stockpricelevel_new TO StockPriceLevel;

-- 驗證
SELECT 'StockPriceLevel（新，分區）'   AS tbl, COUNT(*) AS cnt FROM StockPriceLevel
UNION ALL
SELECT 'stockpricelevel_backup（舊）'  AS tbl, COUNT(*) AS cnt FROM stockpricelevel_backup;

-- 確認分區已建立
SELECT PARTITION_NAME, PARTITION_DESCRIPTION, TABLE_ROWS
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = 'stockapp' AND TABLE_NAME = 'StockPriceLevel'
ORDER BY PARTITION_ORDINAL_POSITION;

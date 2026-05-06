-- =============================================================================
-- 12_stockdataema_rename.sql
-- 目的：原子 RENAME，將 stockdataema_new 上線，原表備份為 stockdataema_backup
-- 預期結果：
--   - StockDataEMA（分區表）可正常查詢，筆數與備份表相同
--   - stockdataema_backup 保留舊資料供回滾使用
-- 前置條件：11_stockdataema_verify.sql 確認筆數一致後才可執行
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 12_stockdataema_rename.sql
--
-- 回滾方式：
--   RENAME TABLE StockDataEMA TO stockdataema_new, stockdataema_backup TO StockDataEMA;
-- =============================================================================

USE stockapp;

RENAME TABLE
    StockDataEMA     TO stockdataema_backup,
    stockdataema_new TO StockDataEMA;

-- 驗證
SELECT 'StockDataEMA（新，分區）'   AS tbl, COUNT(*) AS cnt FROM StockDataEMA
UNION ALL
SELECT 'stockdataema_backup（舊）'  AS tbl, COUNT(*) AS cnt FROM stockdataema_backup;

-- 確認分區已建立
SELECT PARTITION_NAME, PARTITION_DESCRIPTION, TABLE_ROWS
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = 'stockapp' AND TABLE_NAME = 'StockDataEMA'
ORDER BY PARTITION_ORDINAL_POSITION;

-- =============================================================================
-- 16_stockdataturn_rename.sql
-- 目的：原子 RENAME，將 stockdataturn_new 上線，原表備份為 stockdataturn_backup
-- 預期結果：
--   - StockDataTurn（分區表）可正常查詢，筆數與備份表相同
--   - stockdataturn_backup 保留舊資料供回滾使用
-- 前置條件：15_stockdataturn_verify.sql 確認筆數一致後才可執行
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 16_stockdataturn_rename.sql
--
-- 回滾方式：
--   RENAME TABLE StockDataTurn TO stockdataturn_new, stockdataturn_backup TO StockDataTurn;
-- =============================================================================

USE stockapp;

RENAME TABLE
    StockDataTurn     TO stockdataturn_backup,
    stockdataturn_new TO StockDataTurn;

-- 驗證
SELECT 'StockDataTurn（新，分區）'   AS tbl, COUNT(*) AS cnt FROM StockDataTurn
UNION ALL
SELECT 'stockdataturn_backup（舊）'  AS tbl, COUNT(*) AS cnt FROM stockdataturn_backup;

-- 確認分區已建立
SELECT PARTITION_NAME, PARTITION_DESCRIPTION, TABLE_ROWS
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = 'stockapp' AND TABLE_NAME = 'StockDataTurn'
ORDER BY PARTITION_ORDINAL_POSITION;

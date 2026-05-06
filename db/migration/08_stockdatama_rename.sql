-- =============================================================================
-- 08_stockdatama_rename.sql
-- 目的：原子 RENAME，將 stockdatama_new 上線，原表備份為 stockdatama_backup
-- 預期結果：
--   - StockDataMA（分區表）可正常查詢，筆數與備份表相同
--   - stockdatama_backup 保留舊資料供回滾使用
-- 前置條件：07_stockdatama_verify.sql 確認筆數一致後才可執行
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 08_stockdatama_rename.sql
--
-- 回滾方式：
--   RENAME TABLE StockDataMA TO stockdatama_new, stockdatama_backup TO StockDataMA;
-- =============================================================================

USE stockapp;

RENAME TABLE
    StockDataMA     TO stockdatama_backup,
    stockdatama_new TO StockDataMA;

-- 驗證
SELECT 'StockDataMA（新，分區）'   AS tbl, COUNT(*) AS cnt FROM StockDataMA
UNION ALL
SELECT 'stockdatama_backup（舊）'  AS tbl, COUNT(*) AS cnt FROM stockdatama_backup;

-- 確認分區已建立
SELECT PARTITION_NAME, PARTITION_DESCRIPTION, TABLE_ROWS
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = 'stockapp' AND TABLE_NAME = 'StockDataMA'
ORDER BY PARTITION_ORDINAL_POSITION;

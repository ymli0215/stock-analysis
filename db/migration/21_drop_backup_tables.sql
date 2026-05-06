-- =============================================================================
-- 21_drop_backup_tables.sql
-- 目的：確認系統運作穩定後，刪除 5 張備份表以釋放磁碟空間
-- 預期結果：5 張 _backup 表不再存在
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 21_drop_backup_tables.sql
--
-- ⚠️  警告：此操作不可回滾！
--   - 建議觀察至少 1~2 個完整交易日，確認新分區表正常讀寫後再執行
--   - 執行前再次確認各新表筆數正確（可重跑 03/07/11/15/19 verify 檔）
--   - DROP 後若發現問題，只能從備份還原，無法透過 RENAME 快速回滾
-- =============================================================================

USE stockapp;

-- 執行前最後確認：新表（分區版）筆數總覽
SELECT 'StockData'       AS tbl, COUNT(*) AS cnt FROM StockData
UNION ALL
SELECT 'StockDataMA'     AS tbl, COUNT(*) AS cnt FROM StockDataMA
UNION ALL
SELECT 'StockDataEMA'    AS tbl, COUNT(*) AS cnt FROM StockDataEMA
UNION ALL
SELECT 'StockDataTurn'   AS tbl, COUNT(*) AS cnt FROM StockDataTurn
UNION ALL
SELECT 'StockPriceLevel' AS tbl, COUNT(*) AS cnt FROM StockPriceLevel;

-- 確認分區狀態正常
SELECT TABLE_NAME, COUNT(*) AS partition_count
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = 'stockapp'
  AND TABLE_NAME IN ('StockData','StockDataMA','StockDataEMA','StockDataTurn','StockPriceLevel')
GROUP BY TABLE_NAME;

-- 刪除備份表（確認上方數字無誤後取消下方註解執行）
-- DROP TABLE stockdata_backup;
-- DROP TABLE stockdatama_backup;
-- DROP TABLE stockdataema_backup;
-- DROP TABLE stockdataturn_backup;
-- DROP TABLE stockpricelevel_backup;

-- 執行 DROP 後驗證備份表已不存在
-- （若上方 DROP 已執行，下方查詢應回傳 0 行）
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'stockapp'
  AND TABLE_NAME IN (
      'stockdata_backup',
      'stockdatama_backup',
      'stockdataema_backup',
      'stockdataturn_backup',
      'stockpricelevel_backup'
  );

-- 最終確認：查詢結果應為空（0 rows），代表備份表已全部清除
SELECT COUNT(*) AS remaining_backup_tables
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'stockapp'
  AND TABLE_NAME LIKE '%_backup';

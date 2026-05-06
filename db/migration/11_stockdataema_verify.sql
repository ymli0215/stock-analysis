-- =============================================================================
-- 11_stockdataema_verify.sql
-- 目的：詳細比對 StockDataEMA 與 stockdataema_new 的筆數，確認搬移完整
-- 預期結果：總筆數、各 dataType、各年度筆數全部相等，diff 為 0
-- 前置條件：10_stockdataema_insert.sql 已成功執行
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 11_stockdataema_verify.sql
-- =============================================================================

USE stockapp;

-- 1. 總筆數比對
SELECT 'StockDataEMA'     AS tbl, COUNT(*) AS cnt FROM StockDataEMA
UNION ALL
SELECT 'stockdataema_new' AS tbl, COUNT(*) AS cnt FROM stockdataema_new;

-- 2. 依 dataType 分組比對
SELECT 'StockDataEMA'     AS section, dataType, COUNT(*) AS cnt FROM StockDataEMA     GROUP BY dataType
UNION ALL
SELECT 'stockdataema_new' AS section, dataType, COUNT(*) AS cnt FROM stockdataema_new GROUP BY dataType
ORDER BY dataType, section;

-- 3. 依年度分組比對（diff 應全為 0）
SELECT YEAR(dataDate) AS yr,
       SUM(CASE WHEN src = 'old' THEN cnt ELSE 0 END) AS old_cnt,
       SUM(CASE WHEN src = 'new' THEN cnt ELSE 0 END) AS new_cnt,
       SUM(CASE WHEN src = 'old' THEN cnt ELSE 0 END) -
       SUM(CASE WHEN src = 'new' THEN cnt ELSE 0 END) AS diff
FROM (
    SELECT YEAR(dataDate) AS dataDate, 'old' AS src, COUNT(*) AS cnt
    FROM StockDataEMA GROUP BY YEAR(dataDate)
    UNION ALL
    SELECT YEAR(dataDate) AS dataDate, 'new' AS src, COUNT(*) AS cnt
    FROM stockdataema_new GROUP BY YEAR(dataDate)
) t
GROUP BY yr
ORDER BY yr;

-- 最終確認
SELECT COUNT(*) AS StockDataEMA_cnt     FROM StockDataEMA;
SELECT COUNT(*) AS stockdataema_new_cnt FROM stockdataema_new;

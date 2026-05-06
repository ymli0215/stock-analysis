-- =============================================================================
-- 15_stockdataturn_verify.sql
-- 目的：詳細比對 StockDataTurn 與 stockdataturn_new 的筆數，確認搬移完整
-- 預期結果：總筆數、各 dataType、各年度筆數全部相等，diff 為 0
-- 前置條件：14_stockdataturn_insert.sql 已成功執行
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 15_stockdataturn_verify.sql
-- =============================================================================

USE stockapp;

-- 1. 總筆數比對
SELECT 'StockDataTurn'     AS tbl, COUNT(*) AS cnt FROM StockDataTurn
UNION ALL
SELECT 'stockdataturn_new' AS tbl, COUNT(*) AS cnt FROM stockdataturn_new;

-- 2. 依 dataType 分組比對
SELECT 'StockDataTurn'     AS section, dataType, COUNT(*) AS cnt FROM StockDataTurn     GROUP BY dataType
UNION ALL
SELECT 'stockdataturn_new' AS section, dataType, COUNT(*) AS cnt FROM stockdataturn_new GROUP BY dataType
ORDER BY dataType, section;

-- 3. 依年度分組比對（diff 應全為 0）
SELECT yr,
       SUM(CASE WHEN src = 'old' THEN cnt ELSE 0 END) AS old_cnt,
       SUM(CASE WHEN src = 'new' THEN cnt ELSE 0 END) AS new_cnt,
       SUM(CASE WHEN src = 'old' THEN cnt ELSE 0 END) -
       SUM(CASE WHEN src = 'new' THEN cnt ELSE 0 END) AS diff
FROM (
    SELECT YEAR(FROM_UNIXTIME(dataTime/1000)) AS yr, 'old' AS src, COUNT(*) AS cnt
    FROM StockDataTurn GROUP BY YEAR(FROM_UNIXTIME(dataTime/1000))
    UNION ALL
    SELECT dataYear AS yr, 'new' AS src, COUNT(*) AS cnt
    FROM stockdataturn_new GROUP BY dataYear
) t
GROUP BY yr
ORDER BY yr;

-- 最終確認
SELECT COUNT(*) AS StockDataTurn_cnt     FROM StockDataTurn;
SELECT COUNT(*) AS stockdataturn_new_cnt FROM stockdataturn_new;

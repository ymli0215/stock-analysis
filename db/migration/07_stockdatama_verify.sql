-- =============================================================================
-- 07_stockdatama_verify.sql
-- 目的：詳細比對 StockDataMA 與 stockdatama_new 的筆數，確認搬移完整
-- 預期結果：總筆數、各 dataType、各年度筆數全部相等，diff 為 0
-- 前置條件：06_stockdatama_insert.sql 已成功執行
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 07_stockdatama_verify.sql
-- =============================================================================

USE stockapp;

-- 1. 總筆數比對
SELECT 'StockDataMA'     AS tbl, COUNT(*) AS cnt FROM StockDataMA
UNION ALL
SELECT 'stockdatama_new' AS tbl, COUNT(*) AS cnt FROM stockdatama_new;

-- 2. 依 dataType 分組比對
SELECT 'StockDataMA'     AS section, dataType, COUNT(*) AS cnt FROM StockDataMA     GROUP BY dataType
UNION ALL
SELECT 'stockdatama_new' AS section, dataType, COUNT(*) AS cnt FROM stockdatama_new GROUP BY dataType
ORDER BY dataType, section;

-- 3. 依年度分組比對（diff 應全為 0）
SELECT YEAR(dataDate) AS yr,
       SUM(CASE WHEN src = 'old' THEN cnt ELSE 0 END) AS old_cnt,
       SUM(CASE WHEN src = 'new' THEN cnt ELSE 0 END) AS new_cnt,
       SUM(CASE WHEN src = 'old' THEN cnt ELSE 0 END) -
       SUM(CASE WHEN src = 'new' THEN cnt ELSE 0 END) AS diff
FROM (
    SELECT YEAR(dataDate) AS dataDate, 'old' AS src, COUNT(*) AS cnt
    FROM StockDataMA GROUP BY YEAR(dataDate)
    UNION ALL
    SELECT YEAR(dataDate) AS dataDate, 'new' AS src, COUNT(*) AS cnt
    FROM stockdatama_new GROUP BY YEAR(dataDate)
) t
GROUP BY yr
ORDER BY yr;

-- 最終確認
SELECT COUNT(*) AS StockDataMA_cnt     FROM StockDataMA;
SELECT COUNT(*) AS stockdatama_new_cnt FROM stockdatama_new;

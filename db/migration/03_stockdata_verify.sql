-- =============================================================================
-- 03_stockdata_verify.sql
-- 目的：詳細比對 StockData 與 stockdata_new 的筆數，確認搬移完整
-- 預期結果：
--   1. 總筆數相等
--   2. 各 dataType 分組筆數相等
--   3. 各年度分組筆數相等
-- 前置條件：02_stockdata_insert.sql 已成功執行
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 03_stockdata_verify.sql
-- =============================================================================

USE stockapp;

-- 1. 總筆數比對（兩行數字應相同）
SELECT '=== 總筆數比對 ===' AS check_type, '' AS detail, '' AS old_cnt, '' AS new_cnt
UNION ALL
SELECT 'StockData 總筆數' AS check_type,
       '' AS detail,
       CAST((SELECT COUNT(*) FROM StockData) AS CHAR) AS old_cnt,
       CAST((SELECT COUNT(*) FROM stockdata_new) AS CHAR) AS new_cnt;

-- 2. 依 dataType 分組比對（D/W/M 各自對齊）
SELECT '=== 依 dataType 分組 ===' AS section, '' AS dataType, 0 AS old_cnt, 0 AS new_cnt
UNION ALL
SELECT 'StockData'     AS section, dataType, COUNT(*) AS old_cnt, 0 FROM StockData     GROUP BY dataType
UNION ALL
SELECT 'stockdata_new' AS section, dataType, 0, COUNT(*) AS new_cnt FROM stockdata_new GROUP BY dataType
ORDER BY dataType, section;

-- 3. 依年度分組比對
SELECT YEAR(dataDate) AS yr,
       SUM(CASE WHEN src = 'old' THEN cnt ELSE 0 END) AS old_cnt,
       SUM(CASE WHEN src = 'new' THEN cnt ELSE 0 END) AS new_cnt,
       SUM(CASE WHEN src = 'old' THEN cnt ELSE 0 END) -
       SUM(CASE WHEN src = 'new' THEN cnt ELSE 0 END) AS diff
FROM (
    SELECT YEAR(dataDate) AS dataDate, 'old' AS src, COUNT(*) AS cnt
    FROM StockData GROUP BY YEAR(dataDate)
    UNION ALL
    SELECT YEAR(dataDate) AS dataDate, 'new' AS src, COUNT(*) AS cnt
    FROM stockdata_new GROUP BY YEAR(dataDate)
) t
GROUP BY yr
ORDER BY yr;

-- 最終確認：兩表 COUNT 應一致，diff 全部為 0
SELECT COUNT(*) AS StockData_cnt     FROM StockData;
SELECT COUNT(*) AS stockdata_new_cnt FROM stockdata_new;

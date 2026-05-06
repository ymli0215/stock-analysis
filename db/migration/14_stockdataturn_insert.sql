-- =============================================================================
-- 14_stockdataturn_insert.sql
-- 目的：將 StockDataTurn 資料依年份分批搬入 stockdataturn_new（5 年一批）
-- 預期結果：stockdataturn_new 的 COUNT(*) 與 StockDataTurn 相等
-- 前置條件：13_stockdataturn_create.sql 已成功執行
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 14_stockdataturn_insert.sql
--
-- 注意：欄位列表刻意跳過 value13 與 value28（這兩個欄位不存在）
-- =============================================================================

USE stockapp;

INSERT INTO stockdataturn_new
    (stockId,dataType,dataTime,dataYear,middle,
     value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
     value11,value12,value14,value15,value16,value17,value18,value19,value20,
     value21,value22,value23,value24,value25,value26,value27,value29,value30,value31)
SELECT  stockId,dataType,dataTime,YEAR(FROM_UNIXTIME(dataTime/1000)),middle,
        value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
        value11,value12,value14,value15,value16,value17,value18,value19,value20,
        value21,value22,value23,value24,value25,value26,value27,value29,value30,value31
FROM    StockDataTurn WHERE YEAR(FROM_UNIXTIME(dataTime/1000)) < 2010;

INSERT INTO stockdataturn_new
    (stockId,dataType,dataTime,dataYear,middle,
     value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
     value11,value12,value14,value15,value16,value17,value18,value19,value20,
     value21,value22,value23,value24,value25,value26,value27,value29,value30,value31)
SELECT  stockId,dataType,dataTime,YEAR(FROM_UNIXTIME(dataTime/1000)),middle,
        value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
        value11,value12,value14,value15,value16,value17,value18,value19,value20,
        value21,value22,value23,value24,value25,value26,value27,value29,value30,value31
FROM    StockDataTurn WHERE YEAR(FROM_UNIXTIME(dataTime/1000)) >= 2010 AND YEAR(FROM_UNIXTIME(dataTime/1000)) < 2015;

INSERT INTO stockdataturn_new
    (stockId,dataType,dataTime,dataYear,middle,
     value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
     value11,value12,value14,value15,value16,value17,value18,value19,value20,
     value21,value22,value23,value24,value25,value26,value27,value29,value30,value31)
SELECT  stockId,dataType,dataTime,YEAR(FROM_UNIXTIME(dataTime/1000)),middle,
        value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
        value11,value12,value14,value15,value16,value17,value18,value19,value20,
        value21,value22,value23,value24,value25,value26,value27,value29,value30,value31
FROM    StockDataTurn WHERE YEAR(FROM_UNIXTIME(dataTime/1000)) >= 2015 AND YEAR(FROM_UNIXTIME(dataTime/1000)) < 2020;

INSERT INTO stockdataturn_new
    (stockId,dataType,dataTime,dataYear,middle,
     value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
     value11,value12,value14,value15,value16,value17,value18,value19,value20,
     value21,value22,value23,value24,value25,value26,value27,value29,value30,value31)
SELECT  stockId,dataType,dataTime,YEAR(FROM_UNIXTIME(dataTime/1000)),middle,
        value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
        value11,value12,value14,value15,value16,value17,value18,value19,value20,
        value21,value22,value23,value24,value25,value26,value27,value29,value30,value31
FROM    StockDataTurn WHERE YEAR(FROM_UNIXTIME(dataTime/1000)) >= 2020 AND YEAR(FROM_UNIXTIME(dataTime/1000)) < 2024;

INSERT INTO stockdataturn_new
    (stockId,dataType,dataTime,dataYear,middle,
     value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
     value11,value12,value14,value15,value16,value17,value18,value19,value20,
     value21,value22,value23,value24,value25,value26,value27,value29,value30,value31)
SELECT  stockId,dataType,dataTime,YEAR(FROM_UNIXTIME(dataTime/1000)),middle,
        value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
        value11,value12,value14,value15,value16,value17,value18,value19,value20,
        value21,value22,value23,value24,value25,value26,value27,value29,value30,value31
FROM    StockDataTurn WHERE YEAR(FROM_UNIXTIME(dataTime/1000)) >= 2024;

-- 驗證：新表筆數應與原表相等
SELECT 'StockDataTurn'     AS tbl, COUNT(*) AS cnt FROM StockDataTurn
UNION ALL
SELECT 'stockdataturn_new' AS tbl, COUNT(*) AS cnt FROM stockdataturn_new;

-- =============================================================================
-- 13_stockdataturn_create.sql
-- 目的：建立含年份分區的 stockdataturn_new 空表
-- 預期結果：stockdataturn_new 表存在，COUNT(*) = 0
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 13_stockdataturn_create.sql
--
-- 重要：value13 與 value28 欄位不存在（跳號），本表完全依照現有 Entity 定義。
--       勿假設欄位連號，加欄位時請確認 StockDataTurn.java。
-- =============================================================================

USE stockapp;

CREATE TABLE IF NOT EXISTS stockdataturn_new (
    stockId  VARCHAR(50) NOT NULL,
    dataType VARCHAR(2)  NOT NULL,
    dataTime BIGINT      NOT NULL,
    dataYear INT         NOT NULL,   -- YEAR(FROM_UNIXTIME(dataTime/1000))，分區鍵
    middle   DOUBLE,   -- 日多空中軸
    value1   DOUBLE,   -- DD開盤破站不上空
    value2   DOUBLE,   -- 易多空線
    value3   DOUBLE,   -- 超強
    value4   DOUBLE,   -- 乖離短賣
    value5   DOUBLE,   -- 高控多停利
    value6   DOUBLE,   -- 低控空回補
    value7   DOUBLE,   -- 嘎空點
    value8   DOUBLE,   -- D殺多
    value9   DOUBLE,   -- 末跌
    value10  DOUBLE,   -- 日續跌
    value11  DOUBLE,   -- 破續跌續空
    value12  DOUBLE,   -- 碰主跌=搶短點
    -- value13 不存在（跳號）
    value14  DOUBLE,   -- 碰主跌3
    value15  DOUBLE,   -- 彈仍跌(放空)
    value16  DOUBLE,   -- 回跌
    value17  DOUBLE,   -- 回檔買點/反彈賣點
    value18  DOUBLE,   -- 時K反彈不該過中軸
    value19  DOUBLE,   -- 盤跌
    value20  DOUBLE,   -- T盤漲
    value21  DOUBLE,   -- 反彈峰B
    value22  DOUBLE,   -- 續漲不破續多
    value23  DOUBLE,   -- 過起漲3
    value24  DOUBLE,   -- 過起漲
    value25  DOUBLE,   -- 日續漲
    value26  DOUBLE,   -- 主升
    value27  DOUBLE,   -- 強波2000點
    -- value28 不存在（跳號）
    value29  DOUBLE,   -- 超跌17
    value30  DOUBLE,   -- 超跌16
    value31  DOUBLE,   -- 超跌15
    PRIMARY KEY (stockId, dataType, dataTime, dataYear),
    INDEX idx_stock_type_time (stockId, dataType, dataTime)
)
ENGINE = InnoDB
DEFAULT CHARSET = utf8mb4
PARTITION BY RANGE (dataYear) (
    PARTITION p2000 VALUES LESS THAN (2001),
    PARTITION p2001 VALUES LESS THAN (2002),
    PARTITION p2002 VALUES LESS THAN (2003),
    PARTITION p2003 VALUES LESS THAN (2004),
    PARTITION p2004 VALUES LESS THAN (2005),
    PARTITION p2005 VALUES LESS THAN (2006),
    PARTITION p2006 VALUES LESS THAN (2007),
    PARTITION p2007 VALUES LESS THAN (2008),
    PARTITION p2008 VALUES LESS THAN (2009),
    PARTITION p2009 VALUES LESS THAN (2010),
    PARTITION p2010 VALUES LESS THAN (2011),
    PARTITION p2011 VALUES LESS THAN (2012),
    PARTITION p2012 VALUES LESS THAN (2013),
    PARTITION p2013 VALUES LESS THAN (2014),
    PARTITION p2014 VALUES LESS THAN (2015),
    PARTITION p2015 VALUES LESS THAN (2016),
    PARTITION p2016 VALUES LESS THAN (2017),
    PARTITION p2017 VALUES LESS THAN (2018),
    PARTITION p2018 VALUES LESS THAN (2019),
    PARTITION p2019 VALUES LESS THAN (2020),
    PARTITION p2020 VALUES LESS THAN (2021),
    PARTITION p2021 VALUES LESS THAN (2022),
    PARTITION p2022 VALUES LESS THAN (2023),
    PARTITION p2023 VALUES LESS THAN (2024),
    PARTITION p2024 VALUES LESS THAN (2025),
    PARTITION p2025 VALUES LESS THAN (2026),
    PARTITION p2026 VALUES LESS THAN (2027),
    PARTITION p2027 VALUES LESS THAN (2028),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- 驗證：新表應已建立，筆數為 0
SELECT 'stockdataturn_new 建立完成' AS status, COUNT(*) AS cnt FROM stockdataturn_new;

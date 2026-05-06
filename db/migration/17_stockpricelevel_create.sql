-- =============================================================================
-- 17_stockpricelevel_create.sql
-- 目的：建立含年份分區的 stockpricelevel_new 空表
-- 預期結果：stockpricelevel_new 表存在，COUNT(*) = 0
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 17_stockpricelevel_create.sql
--
-- 欄位說明：
--   h12/l12        12 週期最高/最低
--   level1~level9  多空九軸（買耗/嘎空/轉強/中軸/回撐/轉弱/殺多/賣耗/多空線）
--   turn           轉折點
-- =============================================================================

USE stockapp;

CREATE TABLE IF NOT EXISTS stockpricelevel_new (
    stockId  VARCHAR(50) NOT NULL,
    dataType VARCHAR(2)  NOT NULL,
    dataTime BIGINT      NOT NULL,
    dataDate DATETIME    NOT NULL,
    h12      DOUBLE,
    l12      DOUBLE,
    level1   DOUBLE,   -- 買耗
    level2   DOUBLE,   -- 嘎空
    level3   DOUBLE,   -- 轉強
    level4   DOUBLE,   -- 中軸
    level5   DOUBLE,   -- 回撐
    level6   DOUBLE,   -- 轉弱
    level7   DOUBLE,   -- 殺多
    level8   DOUBLE,   -- 賣耗
    level9   DOUBLE,   -- 多空線
    turn     DOUBLE,   -- 轉折
    PRIMARY KEY (stockId, dataType, dataTime, dataDate),
    INDEX idx_stock_type_time (stockId, dataType, dataDate)
)
ENGINE = InnoDB
DEFAULT CHARSET = utf8mb4
PARTITION BY RANGE (YEAR(dataDate)) (
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
SELECT 'stockpricelevel_new 建立完成' AS status, COUNT(*) AS cnt FROM stockpricelevel_new;

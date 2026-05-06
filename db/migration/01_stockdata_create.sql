-- =============================================================================
-- 01_stockdata_create.sql
-- 目的：建立含年份分區的 stockdata_new 空表
-- 預期結果：stockdata_new 表存在，COUNT(*) = 0
--
-- 執行方式（在 SQL 檔案所在目錄執行）：
-- docker exec -i mysql55 mysql -u root -pca27an12C stockapp < 01_stockdata_create.sql
--
-- 注意：
--   - PK 加入 dataDate 以滿足 MySQL RANGE 分區限制
--   - volume / volume2 已改為 BIGINT
--   - 若表已存在會跳過（IF NOT EXISTS）
-- =============================================================================

USE stockapp;

CREATE TABLE IF NOT EXISTS stockdata_new (
    stockId   VARCHAR(50)  NOT NULL,
    dataType  VARCHAR(2)   NOT NULL,
    dataTime  BIGINT       NOT NULL,
    dataDate  DATETIME     NOT NULL,
    open      DOUBLE,
    close     DOUBLE,
    high      DOUBLE,
    low       DOUBLE,
    volume    BIGINT,
    volume2   BIGINT,
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
SELECT 'stockdata_new 建立完成' AS status, COUNT(*) AS cnt FROM stockdata_new;

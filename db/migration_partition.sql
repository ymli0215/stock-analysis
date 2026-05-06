-- =============================================================================
-- migration_partition.sql
-- 目的：對 5 張大表實施 RANGE 年份分區，以改善大資料量查詢效能
-- 作者：Stock Analysis 專案
-- 日期：2026-04-20
--
-- 影響範圍：
--   StockData、StockDataMA、StockDataEMA、StockDataTurn、StockPriceLevel
--
-- 執行前提：
--   1. 確認 MySQL 版本 >= 5.5，且 InnoDB 支援分區（SHOW PLUGINS 確認 partition）
--   2. 確認磁碟空間充足（搬資料期間需要 2x 原表空間）
--   3. 建議在離峰時段執行，搬資料步驟會有 INSERT 鎖
--   4. 每一個「PHASE」區塊可獨立執行，建議逐段執行並核對驗證結果
--
-- 分區設計說明：
--   - 按 YEAR(dataDate) 做 RANGE 分區（2000 ~ 2027 年 + MAXVALUE catch-all）
--   - MySQL RANGE 分區要求：分區欄位必須包含在所有 UNIQUE KEY（含 PK）中
--   - 因此新表 PK 改為 (stockId, dataType, dataTime, dataDate)
--   - dataTime 與 dataDate 語義相同（相差 epoch ↔ DATETIME），加入 PK 不會
--     產生邏輯重複，只是滿足 MySQL 分區限制
--
-- 回滾方式：
--   執行步驟 X（rename to backup）後若需回滾：
--     RENAME TABLE StockData TO StockData_new,
--                  StockData_backup TO StockData;
-- =============================================================================

USE stockapp;

-- =============================================================================
-- ██████████████████████████████████████████████████████████████████████████
-- █  PHASE 1 — StockData
-- ██████████████████████████████████████████████████████████████████████████
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 步驟 1-A：建立新表（含年份分區）
-- PK 加入 dataDate 以滿足 MySQL 分區限制；其餘欄位與舊表完全相同。
-- volume / volume2 已在先前 DDL 改為 BIGINT，新表沿用。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS StockData_new (
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

-- ----------------------------------------------------------------------------
-- 步驟 1-B：分批搬資料（依年份分批，避免長時間鎖表）
-- 每批約 1 年資料，執行完一批可讓系統稍作緩衝再繼續。
-- 若要更保守可改為按月份或按 stockId 範圍分批。
-- ----------------------------------------------------------------------------
INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate < '2001-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2001-01-01' AND dataDate < '2002-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2002-01-01' AND dataDate < '2003-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2003-01-01' AND dataDate < '2004-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2004-01-01' AND dataDate < '2005-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2005-01-01' AND dataDate < '2006-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2006-01-01' AND dataDate < '2007-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2007-01-01' AND dataDate < '2008-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2008-01-01' AND dataDate < '2009-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2009-01-01' AND dataDate < '2010-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2010-01-01' AND dataDate < '2011-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2011-01-01' AND dataDate < '2012-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2012-01-01' AND dataDate < '2013-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2013-01-01' AND dataDate < '2014-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2014-01-01' AND dataDate < '2015-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2015-01-01' AND dataDate < '2016-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2016-01-01' AND dataDate < '2017-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2017-01-01' AND dataDate < '2018-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2018-01-01' AND dataDate < '2019-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2019-01-01' AND dataDate < '2020-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2020-01-01' AND dataDate < '2021-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2021-01-01' AND dataDate < '2022-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2022-01-01' AND dataDate < '2023-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2023-01-01' AND dataDate < '2024-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2024-01-01' AND dataDate < '2025-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2025-01-01' AND dataDate < '2026-01-01';

INSERT INTO StockData_new
    (stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2)
SELECT  stockId, dataType, dataTime, dataDate, open, close, high, low, volume, volume2
FROM    StockData
WHERE   dataDate >= '2026-01-01';

-- ----------------------------------------------------------------------------
-- 步驟 1-C：驗證 — 比對新舊表筆數（應相等；若有差異請勿繼續）
-- ----------------------------------------------------------------------------
SELECT 'StockData'     AS tbl, COUNT(*) AS cnt FROM StockData
UNION ALL
SELECT 'StockData_new' AS tbl, COUNT(*) AS cnt FROM StockData_new;

-- 也可依 dataType 分組比對，更細緻地確認資料完整性
SELECT dataType, COUNT(*) AS cnt FROM StockData     GROUP BY dataType
UNION ALL
SELECT dataType, COUNT(*) AS cnt FROM StockData_new GROUP BY dataType
ORDER BY dataType, cnt;

-- ----------------------------------------------------------------------------
-- 步驟 1-D：Rename（原子操作，服務停機時間極短）
-- 確認驗證無誤後才執行此步驟。
-- ----------------------------------------------------------------------------
RENAME TABLE
    StockData     TO StockData_backup,
    StockData_new TO StockData;


-- =============================================================================
-- ██████████████████████████████████████████████████████████████████████████
-- █  PHASE 2 — StockDataMA
-- ██████████████████████████████████████████████████████████████████████████
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 步驟 2-A：建立新表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS StockDataMA_new (
    stockId  VARCHAR(50) NOT NULL,
    dataType VARCHAR(2)  NOT NULL,
    dataTime BIGINT      NOT NULL,
    dataDate DATETIME    NOT NULL,
    -- 傳統均線
    ma3      DOUBLE,
    ma5      DOUBLE,
    ma8      DOUBLE,
    ma10     DOUBLE,
    ma13     DOUBLE,
    ma20     DOUBLE,
    ma21     DOUBLE,
    ma34     DOUBLE,
    ma55     DOUBLE,
    ma60     DOUBLE,
    -- Fibonacci 長期均線
    ma89     DOUBLE,
    ma144    DOUBLE,
    ma233    DOUBLE,
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

-- ----------------------------------------------------------------------------
-- 步驟 2-B：分批搬資料（依年份）
-- ----------------------------------------------------------------------------
INSERT INTO StockDataMA_new
    (stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233)
SELECT  stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233
FROM    StockDataMA WHERE dataDate < '2010-01-01';

INSERT INTO StockDataMA_new
    (stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233)
SELECT  stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233
FROM    StockDataMA WHERE dataDate >= '2010-01-01' AND dataDate < '2015-01-01';

INSERT INTO StockDataMA_new
    (stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233)
SELECT  stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233
FROM    StockDataMA WHERE dataDate >= '2015-01-01' AND dataDate < '2020-01-01';

INSERT INTO StockDataMA_new
    (stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233)
SELECT  stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233
FROM    StockDataMA WHERE dataDate >= '2020-01-01' AND dataDate < '2024-01-01';

INSERT INTO StockDataMA_new
    (stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233)
SELECT  stockId,dataType,dataTime,dataDate,ma3,ma5,ma8,ma10,ma13,ma20,ma21,ma34,ma55,ma60,ma89,ma144,ma233
FROM    StockDataMA WHERE dataDate >= '2024-01-01';

-- ----------------------------------------------------------------------------
-- 步驟 2-C：驗證
-- ----------------------------------------------------------------------------
SELECT 'StockDataMA'     AS tbl, COUNT(*) AS cnt FROM StockDataMA
UNION ALL
SELECT 'StockDataMA_new' AS tbl, COUNT(*) AS cnt FROM StockDataMA_new;

-- ----------------------------------------------------------------------------
-- 步驟 2-D：Rename
-- ----------------------------------------------------------------------------
RENAME TABLE
    StockDataMA     TO StockDataMA_backup,
    StockDataMA_new TO StockDataMA;


-- =============================================================================
-- ██████████████████████████████████████████████████████████████████████████
-- █  PHASE 3 — StockDataEMA
-- ██████████████████████████████████████████████████████████████████████████
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 步驟 3-A：建立新表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS StockDataEMA_new (
    stockId  VARCHAR(50) NOT NULL,
    dataType VARCHAR(2)  NOT NULL,
    dataTime BIGINT      NOT NULL,
    dataDate DATETIME    NOT NULL,
    ema3     DOUBLE,
    ema5     DOUBLE,
    ema7     DOUBLE,
    ema8     DOUBLE,
    ema10    DOUBLE,
    ema13    DOUBLE,
    ema20    DOUBLE,
    ema21    DOUBLE,
    ema34    DOUBLE,
    ema53    DOUBLE,
    ema55    DOUBLE,
    ema60    DOUBLE,
    ema89    DOUBLE,
    ema144   DOUBLE,
    ema233   DOUBLE,
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

-- ----------------------------------------------------------------------------
-- 步驟 3-B：分批搬資料
-- ----------------------------------------------------------------------------
INSERT INTO StockDataEMA_new
    (stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233)
SELECT  stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233
FROM    StockDataEMA WHERE dataDate < '2010-01-01';

INSERT INTO StockDataEMA_new
    (stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233)
SELECT  stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233
FROM    StockDataEMA WHERE dataDate >= '2010-01-01' AND dataDate < '2015-01-01';

INSERT INTO StockDataEMA_new
    (stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233)
SELECT  stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233
FROM    StockDataEMA WHERE dataDate >= '2015-01-01' AND dataDate < '2020-01-01';

INSERT INTO StockDataEMA_new
    (stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233)
SELECT  stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233
FROM    StockDataEMA WHERE dataDate >= '2020-01-01' AND dataDate < '2024-01-01';

INSERT INTO StockDataEMA_new
    (stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233)
SELECT  stockId,dataType,dataTime,dataDate,ema3,ema5,ema7,ema8,ema10,ema13,ema20,ema21,ema34,ema53,ema55,ema60,ema89,ema144,ema233
FROM    StockDataEMA WHERE dataDate >= '2024-01-01';

-- ----------------------------------------------------------------------------
-- 步驟 3-C：驗證
-- ----------------------------------------------------------------------------
SELECT 'StockDataEMA'     AS tbl, COUNT(*) AS cnt FROM StockDataEMA
UNION ALL
SELECT 'StockDataEMA_new' AS tbl, COUNT(*) AS cnt FROM StockDataEMA_new;

-- ----------------------------------------------------------------------------
-- 步驟 3-D：Rename
-- ----------------------------------------------------------------------------
RENAME TABLE
    StockDataEMA     TO StockDataEMA_backup,
    StockDataEMA_new TO StockDataEMA;


-- =============================================================================
-- ██████████████████████████████████████████████████████████████████████████
-- █  PHASE 4 — StockDataTurn
-- ██████████████████████████████████████████████████████████████████████████
-- 注意：value13 與 value28 欄位不存在（跳號），本表完全依照現有 Entity 定義。
-- ██████████████████████████████████████████████████████████████████████████
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 步驟 4-A：建立新表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS StockDataTurn_new (
    stockId  VARCHAR(50) NOT NULL,
    dataType VARCHAR(2)  NOT NULL,
    dataTime BIGINT      NOT NULL,
    dataDate DATETIME    NOT NULL,
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

-- ----------------------------------------------------------------------------
-- 步驟 4-B：分批搬資料
-- ----------------------------------------------------------------------------
INSERT INTO StockDataTurn_new
    (stockId,dataType,dataTime,dataDate,middle,
     value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
     value11,value12,value14,value15,value16,value17,value18,value19,value20,
     value21,value22,value23,value24,value25,value26,value27,value29,value30,value31)
SELECT  stockId,dataType,dataTime,dataDate,middle,
        value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
        value11,value12,value14,value15,value16,value17,value18,value19,value20,
        value21,value22,value23,value24,value25,value26,value27,value29,value30,value31
FROM    StockDataTurn WHERE dataDate < '2010-01-01';

INSERT INTO StockDataTurn_new
    (stockId,dataType,dataTime,dataDate,middle,
     value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
     value11,value12,value14,value15,value16,value17,value18,value19,value20,
     value21,value22,value23,value24,value25,value26,value27,value29,value30,value31)
SELECT  stockId,dataType,dataTime,dataDate,middle,
        value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
        value11,value12,value14,value15,value16,value17,value18,value19,value20,
        value21,value22,value23,value24,value25,value26,value27,value29,value30,value31
FROM    StockDataTurn WHERE dataDate >= '2010-01-01' AND dataDate < '2015-01-01';

INSERT INTO StockDataTurn_new
    (stockId,dataType,dataTime,dataDate,middle,
     value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
     value11,value12,value14,value15,value16,value17,value18,value19,value20,
     value21,value22,value23,value24,value25,value26,value27,value29,value30,value31)
SELECT  stockId,dataType,dataTime,dataDate,middle,
        value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
        value11,value12,value14,value15,value16,value17,value18,value19,value20,
        value21,value22,value23,value24,value25,value26,value27,value29,value30,value31
FROM    StockDataTurn WHERE dataDate >= '2015-01-01' AND dataDate < '2020-01-01';

INSERT INTO StockDataTurn_new
    (stockId,dataType,dataTime,dataDate,middle,
     value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
     value11,value12,value14,value15,value16,value17,value18,value19,value20,
     value21,value22,value23,value24,value25,value26,value27,value29,value30,value31)
SELECT  stockId,dataType,dataTime,dataDate,middle,
        value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
        value11,value12,value14,value15,value16,value17,value18,value19,value20,
        value21,value22,value23,value24,value25,value26,value27,value29,value30,value31
FROM    StockDataTurn WHERE dataDate >= '2020-01-01' AND dataDate < '2024-01-01';

INSERT INTO StockDataTurn_new
    (stockId,dataType,dataTime,dataDate,middle,
     value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
     value11,value12,value14,value15,value16,value17,value18,value19,value20,
     value21,value22,value23,value24,value25,value26,value27,value29,value30,value31)
SELECT  stockId,dataType,dataTime,dataDate,middle,
        value1,value2,value3,value4,value5,value6,value7,value8,value9,value10,
        value11,value12,value14,value15,value16,value17,value18,value19,value20,
        value21,value22,value23,value24,value25,value26,value27,value29,value30,value31
FROM    StockDataTurn WHERE dataDate >= '2024-01-01';

-- ----------------------------------------------------------------------------
-- 步驟 4-C：驗證
-- ----------------------------------------------------------------------------
SELECT 'StockDataTurn'     AS tbl, COUNT(*) AS cnt FROM StockDataTurn
UNION ALL
SELECT 'StockDataTurn_new' AS tbl, COUNT(*) AS cnt FROM StockDataTurn_new;

-- ----------------------------------------------------------------------------
-- 步驟 4-D：Rename
-- ----------------------------------------------------------------------------
RENAME TABLE
    StockDataTurn     TO StockDataTurn_backup,
    StockDataTurn_new TO StockDataTurn;


-- =============================================================================
-- ██████████████████████████████████████████████████████████████████████████
-- █  PHASE 5 — StockPriceLevel
-- ██████████████████████████████████████████████████████████████████████████
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 步驟 5-A：建立新表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS StockPriceLevel_new (
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

-- ----------------------------------------------------------------------------
-- 步驟 5-B：分批搬資料
-- ----------------------------------------------------------------------------
INSERT INTO StockPriceLevel_new
    (stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn)
SELECT  stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn
FROM    StockPriceLevel WHERE dataDate < '2010-01-01';

INSERT INTO StockPriceLevel_new
    (stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn)
SELECT  stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn
FROM    StockPriceLevel WHERE dataDate >= '2010-01-01' AND dataDate < '2015-01-01';

INSERT INTO StockPriceLevel_new
    (stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn)
SELECT  stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn
FROM    StockPriceLevel WHERE dataDate >= '2015-01-01' AND dataDate < '2020-01-01';

INSERT INTO StockPriceLevel_new
    (stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn)
SELECT  stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn
FROM    StockPriceLevel WHERE dataDate >= '2020-01-01' AND dataDate < '2024-01-01';

INSERT INTO StockPriceLevel_new
    (stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn)
SELECT  stockId,dataType,dataTime,dataDate,h12,l12,level1,level2,level3,level4,level5,level6,level7,level8,level9,turn
FROM    StockPriceLevel WHERE dataDate >= '2024-01-01';

-- ----------------------------------------------------------------------------
-- 步驟 5-C：驗證
-- ----------------------------------------------------------------------------
SELECT 'StockPriceLevel'     AS tbl, COUNT(*) AS cnt FROM StockPriceLevel
UNION ALL
SELECT 'StockPriceLevel_new' AS tbl, COUNT(*) AS cnt FROM StockPriceLevel_new;

-- ----------------------------------------------------------------------------
-- 步驟 5-D：Rename
-- ----------------------------------------------------------------------------
RENAME TABLE
    StockPriceLevel     TO StockPriceLevel_backup,
    StockPriceLevel_new TO StockPriceLevel;


-- =============================================================================
-- ██████████████████████████████████████████████████████████████████████████
-- █  PHASE 6 — 全體驗證 & 清理備份表
-- ██████████████████████████████████████████████████████████████████████████
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 步驟 6-A：確認所有新表分區正確建立
-- ----------------------------------------------------------------------------
SELECT
    TABLE_NAME,
    PARTITION_NAME,
    PARTITION_ORDINAL_POSITION,
    PARTITION_DESCRIPTION,
    TABLE_ROWS
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = 'stockapp'
  AND TABLE_NAME IN ('StockData','StockDataMA','StockDataEMA','StockDataTurn','StockPriceLevel')
ORDER BY TABLE_NAME, PARTITION_ORDINAL_POSITION;

-- ----------------------------------------------------------------------------
-- 步驟 6-B：總筆數比對（新表 vs 備份表）
-- ----------------------------------------------------------------------------
SELECT 'StockData'          AS tbl, COUNT(*) AS cnt FROM StockData
UNION ALL
SELECT 'StockData_backup'   AS tbl, COUNT(*) AS cnt FROM StockData_backup
UNION ALL
SELECT 'StockDataMA'        AS tbl, COUNT(*) AS cnt FROM StockDataMA
UNION ALL
SELECT 'StockDataMA_backup' AS tbl, COUNT(*) AS cnt FROM StockDataMA_backup
UNION ALL
SELECT 'StockDataEMA'        AS tbl, COUNT(*) AS cnt FROM StockDataEMA
UNION ALL
SELECT 'StockDataEMA_backup' AS tbl, COUNT(*) AS cnt FROM StockDataEMA_backup
UNION ALL
SELECT 'StockDataTurn'        AS tbl, COUNT(*) AS cnt FROM StockDataTurn
UNION ALL
SELECT 'StockDataTurn_backup' AS tbl, COUNT(*) AS cnt FROM StockDataTurn_backup
UNION ALL
SELECT 'StockPriceLevel'        AS tbl, COUNT(*) AS cnt FROM StockPriceLevel
UNION ALL
SELECT 'StockPriceLevel_backup' AS tbl, COUNT(*) AS cnt FROM StockPriceLevel_backup;

-- ----------------------------------------------------------------------------
-- 步驟 6-C：確認系統運作正常後，刪除備份表（不可回滾，請謹慎）
-- 建議觀察 1 ~ 2 個交易日後再執行。
-- ----------------------------------------------------------------------------
-- DROP TABLE StockData_backup;
-- DROP TABLE StockDataMA_backup;
-- DROP TABLE StockDataEMA_backup;
-- DROP TABLE StockDataTurn_backup;
-- DROP TABLE StockPriceLevel_backup;

-- =============================================================================
-- END OF SCRIPT
-- =============================================================================

"""
fetch_nikkei225.py — 日經股票日線歷史資料爬蟲

時間規範（參考 CLAUDE.md §四）：
  - dataType = "D"
  - dataTime = 收盤日當天 08:00:00 台灣時間（UTC+8）的毫秒 epoch
    注意：yfinance 回傳的日期就是收盤日，不需要 -1 day
    （histock 需要 -1 day 是因為 histock timestamp 指向隔天；yfinance 直接給收盤日）
  - dataDate = 同一時刻的 DATETIME（與 dataTime 完全一致）
"""

import os
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path

import pymysql
import pytz
import yfinance as yf
from dotenv import load_dotenv

# ── 設定 ─────────────────────────────────────────────────────
load_dotenv(Path(__file__).parent.parent / ".env")

DB_CONFIG = {
    "host":     os.environ["DB_HOST"],
    "port":     int(os.environ.get("DB_PORT", 3306)),
    "user":     os.environ["DB_USER"],
    "password": os.environ["DB_PASSWORD"],
    "database": os.environ["DB_NAME"],
    "charset":  "utf8mb4",
}

TZ_TAIPEI = pytz.timezone("Asia/Taipei")
DATA_TYPE  = "D"
MARKET     = "JP"
FETCH_PERIOD = "10y"   # yfinance period 參數


# ── 時間轉換（日線專用）────────────────────────────────────────
def to_stock_datatime_daily(trade_date: datetime) -> tuple[int, datetime]:
    """
    yfinance 回傳的 trade_date 是收盤日本身（非隔天），
    直接正規化成台灣 08:00 即可，不需要 -1 day。
    """
    if trade_date.tzinfo is None:
        base = TZ_TAIPEI.localize(trade_date.replace(hour=8, minute=0, second=0, microsecond=0))
    else:
        base = trade_date.astimezone(TZ_TAIPEI).replace(hour=8, minute=0, second=0, microsecond=0)

    data_time_ms = int(base.timestamp() * 1000)
    return data_time_ms, base


# ── Stocks 主檔確保存在 ───────────────────────────────────────
def upsert_stock(conn, stock_id: str, name: str):
    sql = """
        INSERT INTO Stocks (stockId, stockName, stockEngName, stockType, status, market)
        VALUES (%s, %s, %s, 'JP', 1, 'JP')
        ON DUPLICATE KEY UPDATE
            stockName = VALUES(stockName),
            market    = 'JP',
            status    = 1
    """
    with conn.cursor() as cur:
        cur.execute(sql, (stock_id, name, name))
    conn.commit()


# ── 批次寫入 StockData ────────────────────────────────────────
def insert_stock_data(conn, rows: list[dict]) -> int:
    if not rows:
        return 0
    sql = """
        INSERT INTO StockData
            (stockId, dataType, dataTime, open, high, low, close, volume, dataDate)
        VALUES
            (%(stockId)s, %(dataType)s, %(dataTime)s,
             %(open)s, %(high)s, %(low)s, %(close)s, %(volume)s, %(dataDate)s)
        ON DUPLICATE KEY UPDATE
            open     = VALUES(open),
            high     = VALUES(high),
            low      = VALUES(low),
            close    = VALUES(close),
            volume   = VALUES(volume),
            dataDate = VALUES(dataDate)
    """
    with conn.cursor() as cur:
        cur.executemany(sql, rows)
    conn.commit()
    return len(rows)


# ── 單支股票抓取 ──────────────────────────────────────────────
def fetch_one(conn, stock_id: str) -> tuple[int, str | None]:
    """
    Returns: (寫入筆數, 錯誤訊息或 None)
    """
    try:
        ticker = yf.Ticker(stock_id)
        hist   = ticker.history(period=FETCH_PERIOD, auto_adjust=True)

        if hist.empty:
            return 0, f"yfinance 回傳空資料"

        # 取股票名稱（info 可能很慢，先用 stock_id 代替）
        name = stock_id
        upsert_stock(conn, stock_id, name)

        rows = []
        for trade_date, row in hist.iterrows():
            # trade_date 是 pandas Timestamp（UTC or tz-aware）
            dt_py = trade_date.to_pydatetime()
            data_time_ms, data_date = to_stock_datatime_daily(dt_py)

            rows.append({
                "stockId":  stock_id,
                "dataType": DATA_TYPE,
                "dataTime": data_time_ms,
                "open":     round(float(row["Open"]),  2),
                "high":     round(float(row["High"]),  2),
                "low":      round(float(row["Low"]),   2),
                "close":    round(float(row["Close"]), 2),
                "volume":   int(row["Volume"]),
                "dataDate": data_date.strftime("%Y-%m-%d %H:%M:%S"),
            })

        written = insert_stock_data(conn, rows)
        return written, None

    except Exception as e:
        return 0, str(e)


# ── 主流程 ────────────────────────────────────────────────────
def run(stock_list: list[str], label: str = ""):
    conn = pymysql.connect(**DB_CONFIG)
    print(f"\n{'='*55}")
    print(f"  {label or '執行批次'}  共 {len(stock_list)} 支")
    print(f"{'='*55}")

    ok_count   = 0
    fail_list  = []
    t_start    = time.time()

    for i, sid in enumerate(stock_list, 1):
        print(f"[{i:>2}/{len(stock_list)}] {sid} ...", end=" ", flush=True)
        written, err = fetch_one(conn, sid)
        if err:
            print(f"FAIL — {err}")
            fail_list.append((sid, err))
        else:
            print(f"OK  {written} 筆")
            ok_count += written
        time.sleep(0.5)   # 避免被 Yahoo 限速

    conn.close()
    elapsed = time.time() - t_start

    print(f"\n{'─'*55}")
    print(f"  成功寫入：{ok_count} 筆")
    print(f"  失敗清單：{[s for s,_ in fail_list] or '無'}")
    for sid, msg in fail_list:
        print(f"    {sid}: {msg}")
    print(f"  總耗時：{elapsed:.1f} 秒")
    print(f"{'─'*55}\n")

    return ok_count, fail_list


# ── 進入點 ────────────────────────────────────────────────────
if __name__ == "__main__":
    # Phase 1：5 支測試
    TEST_STOCKS = ["4182.T", "5706.T", "6981.T", "6590.T", "6787.T"]

    FULL_STOCKS = [
        "4182.T", "5706.T", "6981.T", "6590.T", "6787.T",
        "285A.T", "3110.T", "4062.T", "6278.T", "7521.T",
        "3407.T", "218A.T", "278A.T", "4186.T", "6104.T", "6857.T",
    ]

    mode = sys.argv[1] if len(sys.argv) > 1 else "test"

    if mode == "test":
        run(TEST_STOCKS, label="Phase 1 — 測試 5 支")
    elif mode == "full":
        run(FULL_STOCKS, label="Phase 2 — 完整 16 支")
    else:
        print(f"未知模式: {mode}  用法: python fetch_nikkei225.py [test|full]")

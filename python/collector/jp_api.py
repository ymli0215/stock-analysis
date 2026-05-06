"""
jp_api.py — FastAPI service for Japanese stock data (Cache-first, port 8090).

POST /api/fetch/jp
  Request : { "stockId": "7203.T", "dataType": "D" }
  Response: { "status": "ok"|"skipped"|"error",
              "action": "full"|"incremental"|"no_action",
              "written": N,
              "message": "..." }

Cache-first logic (DESIGN_NOTES §1.3):
  count=0                    → full       period="10y"
  max_dataTime < today 08:00 → incremental period="{days+15}d"
  max_dataTime >= today 08:00 → skipped   no external call
"""

import os
import time
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

import requests
from dotenv import load_dotenv
from fastapi import FastAPI
from loguru import logger
from pydantic import BaseModel

from collector.db import get_conn

# load .env so YAHOO_COOKIE is available when running standalone
from pathlib import Path
load_dotenv(Path(__file__).parent.parent / ".env")

TZ_TAIPEI = ZoneInfo("Asia/Taipei")
YAHOO_COOKIE = os.getenv("YAHOO_COOKIE", "")

app = FastAPI(title="JP Stock Fetcher", version="1.1.0")


# ── Request model ──────────────────────────────────────────────────────────────

class FetchRequest(BaseModel):
    stockId: str
    dataType: str = "D"


# ── Time helpers ───────────────────────────────────────────────────────────────

def today_08_ms() -> int:
    """Epoch ms for today 08:00 Asia/Taipei."""
    now = datetime.now(TZ_TAIPEI).replace(hour=8, minute=0, second=0, microsecond=0)
    return int(now.timestamp() * 1000)


def to_data_time_ms(trade_date: date, data_type: str) -> int:
    """Convert a trade date (from Yahoo Finance) to dataTime epoch ms.

    yfinance/Yahoo Finance returns the actual closing date, so no -1 day
    adjustment is needed (unlike histock which returns the next day).

    Args:
        trade_date: The closing date as returned by Yahoo Finance.
        data_type:  "D", "W", "M", or "Y".

    Returns:
        Epoch milliseconds anchored to 08:00 Asia/Taipei of the base date.
    """
    if data_type == "D":
        anchor = trade_date
    elif data_type == "W":
        anchor = trade_date - timedelta(days=trade_date.weekday())  # Monday
    elif data_type == "M":
        anchor = trade_date.replace(day=1)
    else:  # Y or fallback
        anchor = trade_date.replace(month=1, day=1)

    aware = datetime(anchor.year, anchor.month, anchor.day, 8, 0, 0, tzinfo=TZ_TAIPEI)
    return int(aware.timestamp() * 1000)


def ms_to_date(epoch_ms: int) -> date:
    """Convert epoch milliseconds back to a date in Asia/Taipei."""
    return datetime.fromtimestamp(epoch_ms / 1000, tz=TZ_TAIPEI).date()


# ── Cache-first DB query ───────────────────────────────────────────────────────

def query_cache_state(conn, stock_id: str, data_type: str) -> tuple[int, int]:
    """Return (row_count, max_data_time_ms) for the given stock/type.

    max_data_time_ms is 0 when no rows exist.
    """
    with conn.cursor() as cur:
        cur.execute(
            "SELECT COUNT(*), IFNULL(MAX(dataTime), 0) "
            "FROM StockData WHERE stockId=%s AND dataType=%s",
            (stock_id, data_type),
        )
        row = cur.fetchone()
    return (row[0] or 0, row[1] or 0)


def calc_period(max_data_time_ms: int) -> str:
    """Derive yfinance period string from last dataTime.

    Uses days_missing + 15d buffer (per CLAUDE.md §2.6).
    Uses Taipei timezone to avoid UTC date-boundary issues in Docker.
    """
    last_date = ms_to_date(max_data_time_ms)
    today = datetime.now(TZ_TAIPEI).date()
    days_missing = (today - last_date).days
    return f"{days_missing + 15}d"


# ── Yahoo Finance fetch ────────────────────────────────────────────────────────

_INTERVAL_MAP = {"D": "1d", "W": "1wk", "M": "1mo", "Y": "3mo"}


def _make_session() -> requests.Session:
    session = requests.Session()
    session.headers.update({"User-Agent": "Mozilla/5.0"})
    if YAHOO_COOKIE:
        for part in YAHOO_COOKIE.split(";"):
            part = part.strip()
            if "=" in part:
                k, v = part.split("=", 1)
                session.cookies.set(k.strip(), v.strip(), domain=".yahoo.com")
    return session


def fetch_yahoo(stock_id: str, data_type: str, period: str, retries: int = 3) -> list[dict]:
    """Fetch OHLCV from Yahoo Finance v8 API with retry.

    Returns a list of row dicts ready for upsert, or [] on failure.
    """
    interval = _INTERVAL_MAP.get(data_type, "1d")
    url = (
        f"https://query1.finance.yahoo.com/v8/finance/chart/{stock_id}"
        f"?interval={interval}&range={period}"
    )

    for attempt in range(1, retries + 1):
        try:
            resp = _make_session().get(url, timeout=20)
            resp.raise_for_status()
            result = resp.json().get("chart", {}).get("result", [])
            if not result:
                logger.warning(f"{stock_id}: empty result (attempt {attempt})")
                if attempt < retries:
                    time.sleep(3)
                continue

            chart = result[0]
            timestamps = chart.get("timestamp", [])
            ohlcv = chart.get("indicators", {}).get("quote", [{}])[0]

            rows = []
            for i, ts in enumerate(timestamps):
                trade_date = datetime.fromtimestamp(ts, tz=TZ_TAIPEI).date()
                data_time = to_data_time_ms(trade_date, data_type)

                def _safe(key: str, idx: int):
                    lst = ohlcv.get(key)
                    return lst[idx] if lst and idx < len(lst) and lst[idx] is not None else None

                rows.append({
                    "stockId":  stock_id,
                    "dataType": data_type,
                    "dataTime": data_time,
                    "open":     _safe("open", i),
                    "high":     _safe("high", i),
                    "low":      _safe("low", i),
                    "close":    _safe("close", i),
                    "volume":   _safe("volume", i),
                    "dataDate": datetime(
                        trade_date.year, trade_date.month, trade_date.day,
                        8, 0, 0
                    ).strftime("%Y-%m-%d %H:%M:%S"),
                })
            return rows

        except Exception as exc:
            logger.error(f"{stock_id}: Yahoo API error attempt {attempt}: {exc}")
            if attempt < retries:
                time.sleep(3)

    return []


# ── DB write ───────────────────────────────────────────────────────────────────

def upsert_stock_data(conn, rows: list[dict]) -> int:
    """UPSERT rows into StockData. Returns affected row count."""
    sql = """
        INSERT INTO StockData
            (stockId, dataType, dataTime, open, high, low, close, volume, dataDate)
        VALUES
            (%(stockId)s, %(dataType)s, %(dataTime)s,
             %(open)s, %(high)s, %(low)s, %(close)s, %(volume)s, %(dataDate)s)
        ON DUPLICATE KEY UPDATE
            open=VALUES(open), high=VALUES(high), low=VALUES(low),
            close=VALUES(close), volume=VALUES(volume), dataDate=VALUES(dataDate)
    """
    with conn.cursor() as cur:
        cur.executemany(sql, rows)
    conn.commit()
    return cur.rowcount


def upsert_stocks(conn, stock_id: str) -> None:
    """Ensure Stocks table has a JP record for the given stockId."""
    sql = """
        INSERT INTO Stocks (stockId, stockName, market, lastUpdateTime)
        VALUES (%s, %s, 'JP', NOW())
        ON DUPLICATE KEY UPDATE market='JP', lastUpdateTime=NOW()
    """
    with conn.cursor() as cur:
        cur.execute(sql, (stock_id, stock_id))
    conn.commit()


# ── API endpoint ───────────────────────────────────────────────────────────────

@app.post("/api/fetch/jp")
def fetch_jp(req: FetchRequest):
    """Cache-first fetch for a Japanese stock.

    Checks DB first; only calls Yahoo Finance when new data is needed.
    """
    stock_id = req.stockId.strip()
    data_type = req.dataType.upper()

    if not stock_id.upper().endswith(".T"):
        return {"status": "error", "action": "no_action", "written": 0,
                "message": "stockId must end with .T for JP stocks"}

    try:
        conn = get_conn()
    except Exception as exc:
        logger.error(f"DB connection failed: {exc}")
        return {"status": "error", "action": "no_action", "written": 0,
                "message": f"DB connection failed: {exc}"}

    try:
        count, max_ms = query_cache_state(conn, stock_id, data_type)
        today_ms = today_08_ms()

        # ── Determine action ────────────────────────────────────────────────
        if count == 0:
            action = "full"
            period = "10y"
        elif max_ms >= today_ms:
            logger.info(f"{stock_id} [{data_type}] already up-to-date (max={max_ms})")
            return {"status": "skipped", "action": "no_action", "written": 0,
                    "message": "already up-to-date"}
        else:
            action = "incremental"
            period = calc_period(max_ms)

        logger.info(f"{stock_id} [{data_type}] action={action} period={period}")

        # ── Fetch & write ───────────────────────────────────────────────────
        rows = fetch_yahoo(stock_id, data_type, period)
        if not rows:
            return {"status": "error", "action": action, "written": 0,
                    "message": "Yahoo Finance returned no data after retries"}

        written = upsert_stock_data(conn, rows)
        upsert_stocks(conn, stock_id)

        logger.info(f"{stock_id} [{data_type}] written={written}")
        return {"status": "ok", "action": action, "written": written, "message": ""}

    except Exception as exc:
        logger.exception(f"fetch_jp unexpected error: {exc}")
        return {"status": "error", "action": "unknown", "written": 0, "message": str(exc)}

    finally:
        conn.close()


# ── Health check ───────────────────────────────────────────────────────────────

@app.post("/api/fetch/fundamental")
def fetch_fundamental_endpoint(req: FetchRequest):
    """Fetch and store fundamental data for one stock.

    Request : { "stockId": "7203.T", "dataType": "D" }
    Response: { "status": "ok"|"error", "snap_written": N, "quarterly_written": N, "message": "..." }
    """
    from collector.fundamental import fetch_fundamental
    market = "JP" if req.stockId.endswith(".T") else \
             "TW" if req.stockId.endswith(".TW") else "US"
    return fetch_fundamental(req.stockId, market)


@app.get("/health")
def health():
    return {"status": "ok"}


# ── Entry point ────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("collector.jp_api:app", host="0.0.0.0", port=8090, reload=False)

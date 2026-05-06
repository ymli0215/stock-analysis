"""FastAPI service for fetching Japanese stock data via yfinance and upserting into stockapp.stockdata."""

import os
import time
from datetime import date, datetime, timedelta

import pymysql
import requests
import yfinance as yf
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from loguru import logger
from pydantic import BaseModel
from zoneinfo import ZoneInfo

load_dotenv()

app = FastAPI(title="JP Stock Fetcher", version="1.0.0")

TZ_TAIPEI = ZoneInfo("Asia/Taipei")
YAHOO_COOKIE = os.getenv("YAHOO_COOKIE", "")

DB_CONFIG = {
    "host": os.getenv("DB_HOST", "mysql55"),
    "port": int(os.getenv("DB_PORT", 3306)),
    "user": os.getenv("DB_USER", "root"),
    "password": os.getenv("DB_PASSWORD", ""),
    "database": "stockapp",
    "charset": "utf8mb4",
}


class FetchRequest(BaseModel):
    stockId: str
    dataType: str = "D"


def get_conn():
    return pymysql.connect(**DB_CONFIG)


def calc_fetch_period(last_update_date: date | None) -> str | None:
    """計算 yfinance 補抓範圍。

    Returns:
        yfinance period 字串，或 None 表示今天已更新無需補抓。
    """
    if last_update_date is None:
        return "10y"
    days_missing = (date.today() - last_update_date).days
    if days_missing <= 0:
        return None
    return f"{days_missing + 15}d"


def get_last_update_date(conn, stock_id: str, data_type: str) -> date | None:
    """查詢 DB 中該股票最後一筆資料的日期。"""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT MAX(dataDate) FROM stockdata WHERE stockId=%s AND dataType=%s",
            (stock_id, data_type),
        )
        row = cur.fetchone()
    if row and row[0]:
        val = row[0]
        return val.date() if isinstance(val, datetime) else val
    return None


def to_data_time(dt: date, data_type: str) -> int:
    """日期轉 dataTime 毫秒 epoch，依線別套用台灣時間 08:00 規則。"""
    if data_type == "D":
        anchor = dt - timedelta(days=1)
    elif data_type == "W":
        anchor = dt - timedelta(days=dt.weekday())  # 週一
    elif data_type == "M":
        anchor = dt.replace(day=1)
    elif data_type == "Y":
        anchor = dt.replace(month=1, day=1)
    else:
        anchor = dt
    aware = datetime(anchor.year, anchor.month, anchor.day, 8, 0, 0, tzinfo=TZ_TAIPEI)
    return int(aware.timestamp() * 1000)


def _make_session() -> requests.Session:
    """建立帶有 Yahoo cookie 的 requests session。"""
    session = requests.Session()
    session.headers.update({"User-Agent": "Mozilla/5.0"})
    if YAHOO_COOKIE:
        for part in YAHOO_COOKIE.split(";"):
            part = part.strip()
            if "=" in part:
                k, v = part.split("=", 1)
                session.cookies.set(k.strip(), v.strip(), domain=".yahoo.com")
    return session


def fetch_yfinance(stock_id: str, data_type: str, period: str, retries: int = 3) -> list[dict]:
    """直接呼叫 Yahoo Finance v8 API 抓歷史資料，失敗最多重試 retries 次。"""
    interval_map = {"D": "1d", "W": "1wk", "M": "1mo", "Y": "3mo"}
    interval = interval_map.get(data_type, "1d")
    url = (
        f"https://query1.finance.yahoo.com/v8/finance/chart/{stock_id}"
        f"?interval={interval}&range={period}"
    )

    for attempt in range(1, retries + 1):
        try:
            session = _make_session()
            resp = session.get(url, timeout=20)
            resp.raise_for_status()
            data = resp.json()
            result = data.get("chart", {}).get("result", [])
            if not result:
                logger.warning(f"{stock_id} Yahoo API returned no result (attempt {attempt})")
                if attempt < retries:
                    time.sleep(3)
                    continue
                return []

            chart = result[0]
            timestamps = chart.get("timestamp", [])
            ohlcv = chart.get("indicators", {}).get("quote", [{}])[0]
            records = []
            for i, ts in enumerate(timestamps):
                row_date = datetime.fromtimestamp(ts, tz=TZ_TAIPEI).date()
                data_time = to_data_time(row_date, data_type)

                def safe(lst, idx):
                    v = lst[idx] if lst and idx < len(lst) else None
                    return v if v is not None else None

                records.append(
                    {
                        "dataTime": data_time,
                        "dataType": data_type,
                        "stockId": stock_id,
                        "open": safe(ohlcv.get("open"), i),
                        "high": safe(ohlcv.get("high"), i),
                        "low": safe(ohlcv.get("low"), i),
                        "close": safe(ohlcv.get("close"), i),
                        "volume": safe(ohlcv.get("volume"), i),
                        "dataDate": row_date,
                    }
                )
            return records
        except Exception as exc:
            logger.error(f"{stock_id} Yahoo API error (attempt {attempt}): {exc}")
            if attempt < retries:
                time.sleep(3)
    return []


def upsert_stockdata(conn, records: list[dict]) -> int:
    """UPSERT 價格資料進 stockdata，回傳影響列數。"""
    sql = """
        INSERT INTO stockdata
            (dataTime, dataType, stockId, open, high, low, close, volume, dataDate)
        VALUES
            (%(dataTime)s, %(dataType)s, %(stockId)s,
             %(open)s, %(high)s, %(low)s, %(close)s, %(volume)s, %(dataDate)s)
        ON DUPLICATE KEY UPDATE
            open=VALUES(open), high=VALUES(high), low=VALUES(low),
            close=VALUES(close), volume=VALUES(volume), dataDate=VALUES(dataDate)
    """
    with conn.cursor() as cur:
        cur.executemany(sql, records)
    conn.commit()
    return cur.rowcount


def upsert_stocks(conn, stock_id: str) -> None:
    """確保 stocks 表有該日股記錄，market 設為 JP。"""
    sql = """
        INSERT INTO stocks (stockId, stockName, market, lastUpdateTime)
        VALUES (%s, %s, 'JP', NOW())
        ON DUPLICATE KEY UPDATE
            market='JP', lastUpdateTime=NOW()
    """
    with conn.cursor() as cur:
        cur.execute(sql, (stock_id, stock_id))
    conn.commit()


@app.post("/api/fetch/jp")
def fetch_jp(req: FetchRequest):
    """抓取日股資料並寫入 stockapp.stockdata。

    Request body:
        stockId: 日股代碼，必須以 .T 結尾（如 7203.T）
        dataType: D / W / M / Y（預設 D）

    Returns:
        {"status": "ok", "upserted": N} 或 {"status": "up_to_date"}
    """
    if not req.stockId.upper().endswith(".T"):
        raise HTTPException(status_code=400, detail="stockId must end with .T for Japanese stocks")

    try:
        conn = get_conn()
    except Exception as exc:
        logger.error(f"DB connection failed: {exc}")
        raise HTTPException(status_code=500, detail=f"DB connection failed: {exc}")

    try:
        last_date = get_last_update_date(conn, req.stockId, req.dataType)
        period = calc_fetch_period(last_date)
        logger.info(f"{req.stockId} last_date={last_date} period={period}")

        if period is None:
            return {"status": "up_to_date"}

        records = fetch_yfinance(req.stockId, req.dataType, period)
        if not records:
            return {"status": "error", "message": "yfinance returned no data after retries"}

        upserted = upsert_stockdata(conn, records)
        upsert_stocks(conn, req.stockId)
        logger.info(f"{req.stockId} upserted={upserted}")
        return {"status": "ok", "upserted": upserted}

    except Exception as exc:
        logger.error(f"fetch_jp error: {exc}")
        return {"status": "error", "message": str(exc)}

    finally:
        conn.close()


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("jp_api:app", host="0.0.0.0", port=8090, reload=False)

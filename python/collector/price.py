"""OHLCV price data collector using Yahoo Finance chart API directly.

Supports TW (.TW), US (no suffix), and JP (.T) markets.
Implements on-demand refresh: fetch only when stored data is not from today.
Cookie is read from YAHOO_COOKIE in .env to avoid 429 responses.
"""

import os
import time
from datetime import date, datetime, timedelta
from typing import Optional

import pandas as pd
import requests
from dotenv import load_dotenv
from loguru import logger
from sqlalchemy import select

from db.models import CollectorLog, StockInfo, StockPrice
from db.session import get_session

load_dotenv()

_DEFAULT_PERIOD_DAYS = 365 * 3

_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36",
    "Accept": "*/*",
    "Accept-Language": "zh-TW,zh;q=0.9,en;q=0.8",
    "Accept-Encoding": "gzip, deflate, br",
    "Referer": "https://finance.yahoo.com/quote/AAPL/history/?frequency=1wk",
    "Origin": "https://finance.yahoo.com",
    "sec-ch-ua": '"Google Chrome";v="147", "Not.A/Brand";v="8", "Chromium";v="147"',
    "sec-ch-ua-mobile": "?0",
    "sec-ch-ua-platform": '"Windows"',
    "sec-fetch-dest": "empty",
    "sec-fetch-mode": "cors",
    "sec-fetch-site": "same-site",
}

_CHART_URL = (
    "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}"
    "?events=capitalGain|div|split"
    "&formatted=true"
    "&includeAdjustedClose=true"
    "&interval=1d"
    "&period1={period1}"
    "&period2={period2}"
    "&symbol={symbol}"
    "&lang=en-US"
    "&region=US"
)


def _detect_market(symbol: str) -> str:
    """Return market tag based on symbol suffix."""
    if symbol.endswith(".TW"):
        return "TW"
    if symbol.endswith(".T"):
        return "JP"
    return "US"


def _build_session() -> requests.Session:
    """Build a requests.Session with browser-like headers and optional cookie."""
    s = requests.Session()
    s.headers.update(_HEADERS)
    cookie_str = os.getenv("YAHOO_COOKIE", "")
    if cookie_str:
        for part in cookie_str.split(";"):
            part = part.strip()
            if "=" in part:
                k, v = part.split("=", 1)
                s.cookies.set(k.strip(), v.strip())
    return s


def _fetch_chart(symbol: str, start: date, end: date) -> pd.DataFrame:
    """Call Yahoo Finance v8 chart API and return a DataFrame indexed by date.

    Columns: Open, High, Low, Close, Volume, Adj Close
    """
    period1 = int(datetime.combine(start, datetime.min.time()).timestamp())
    period2 = int(datetime.combine(end + timedelta(days=1), datetime.min.time()).timestamp())

    url = _CHART_URL.format(symbol=symbol, period1=period1, period2=period2)
    session = _build_session()

    resp = session.get(url, timeout=30)
    resp.raise_for_status()

    data = resp.json()
    result = data.get("chart", {}).get("result")
    if not result:
        error = data.get("chart", {}).get("error")
        raise ValueError(f"Yahoo Finance returned no result for {symbol}: {error}")

    result = result[0]
    timestamps = result.get("timestamp", [])
    if not timestamps:
        return pd.DataFrame()

    quote = result["indicators"]["quote"][0]
    adjclose_list = result["indicators"].get("adjclose", [{}])[0].get("adjclose", [None] * len(timestamps))

    df = pd.DataFrame(
        {
            "Open": quote.get("open", []),
            "High": quote.get("high", []),
            "Low": quote.get("low", []),
            "Close": quote.get("close", []),
            "Volume": quote.get("volume", []),
            "Adj Close": adjclose_list,
        },
        index=pd.to_datetime(timestamps, unit="s").tz_localize(None).normalize(),
    )
    df.index.name = "Date"
    # Drop rows where all OHLC are null (market-closed padding)
    df.dropna(subset=["Open", "High", "Low", "Close"], how="all", inplace=True)
    return df


def _last_stored_date(symbol: str) -> Optional[date]:
    """Return the most recent date stored in stock_prices for *symbol*, or None."""
    with get_session() as session:
        row = session.execute(
            select(StockPrice.date)
            .where(StockPrice.symbol == symbol)
            .order_by(StockPrice.date.desc())
            .limit(1)
        ).first()
    return row[0] if row else None


def _upsert_prices(symbol: str, df: pd.DataFrame) -> int:
    """Upsert a DataFrame of OHLCV rows into stock_prices.

    Returns the number of rows written.
    """
    if df.empty:
        return 0

    rows_written = 0
    with get_session() as session:
        for row_date, row in df.iterrows():
            row_date = row_date.date() if hasattr(row_date, "date") else row_date
            price = session.execute(
                select(StockPrice).where(
                    StockPrice.symbol == symbol,
                    StockPrice.date == row_date,
                )
            ).scalar_one_or_none()

            if price is None:
                price = StockPrice(symbol=symbol, date=row_date)
                session.add(price)

            price.open = _safe_float(row.get("Open"))
            price.high = _safe_float(row.get("High"))
            price.low = _safe_float(row.get("Low"))
            price.close = _safe_float(row.get("Close"))
            price.volume = _safe_int(row.get("Volume"))
            price.adj_close = _safe_float(row.get("Adj Close") or row.get("Close"))
            price.updated_at = datetime.utcnow()
            rows_written += 1

    return rows_written


def _upsert_stock_info(symbol: str, meta: dict) -> None:
    """Upsert basic company info from chart API meta into stock_info."""
    market = _detect_market(symbol)
    with get_session() as session:
        record = session.execute(
            select(StockInfo).where(StockInfo.symbol == symbol)
        ).scalar_one_or_none()

        if record is None:
            record = StockInfo(symbol=symbol)
            session.add(record)

        record.name = meta.get("longName") or meta.get("shortName") or symbol
        record.market = market
        record.currency = meta.get("currency")
        record.exchange = meta.get("exchangeName")
        record.updated_at = datetime.utcnow()


def _log_collection(
    symbol: str,
    collector_type: str,
    status: str,
    message: str = "",
    rows_upserted: int = 0,
) -> None:
    with get_session() as session:
        session.add(
            CollectorLog(
                symbol=symbol,
                collector_type=collector_type,
                status=status,
                message=message,
                rows_upserted=rows_upserted,
            )
        )


def _safe_float(value) -> Optional[float]:
    try:
        return float(value) if value is not None and not pd.isna(value) else None
    except (TypeError, ValueError):
        return None


def _safe_int(value) -> Optional[int]:
    try:
        return int(value) if value is not None and not pd.isna(value) else None
    except (TypeError, ValueError):
        return None


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------


def fetch_prices(
    symbol: str,
    start: Optional[date] = None,
    end: Optional[date] = None,
) -> int:
    """Fetch OHLCV data from Yahoo Finance chart API and upsert into DB.

    Args:
        symbol: Ticker symbol (e.g. "AAPL", "2330.TW", "7203.T").
        start:  Inclusive start date. Defaults to 3 years ago.
        end:    Inclusive end date. Defaults to today.

    Returns:
        Number of rows upserted.
    """
    if start is None:
        start = date.today() - timedelta(days=_DEFAULT_PERIOD_DAYS)
    if end is None:
        end = date.today()

    logger.info(f"[price] Fetching {symbol}  {start} → {end}")
    try:
        df = _fetch_chart(symbol, start, end)

        if df.empty:
            logger.warning(f"[price] Yahoo Finance returned no data for {symbol}")
            _log_collection(symbol, "price", "error", "Yahoo Finance returned empty result")
            return 0

        rows = _upsert_prices(symbol, df)

        # Best-effort meta upsert from chart API
        try:
            period1 = int(datetime.combine(end - timedelta(days=1), datetime.min.time()).timestamp())
            period2 = int(datetime.combine(end + timedelta(days=1), datetime.min.time()).timestamp())
            url = _CHART_URL.format(symbol=symbol, period1=period1, period2=period2)
            resp = _build_session().get(url, timeout=15)
            if resp.ok:
                meta = resp.json().get("chart", {}).get("result", [{}])[0].get("meta", {})
                _upsert_stock_info(symbol, meta)
        except Exception as meta_exc:
            logger.warning(f"[price] Could not upsert stock info for {symbol}: {meta_exc}")

        _log_collection(symbol, "price", "ok", rows_upserted=rows)
        logger.info(f"[price] {symbol}: {rows} rows upserted.")
        return rows

    except Exception as exc:
        logger.error(f"[price] Failed to fetch {symbol}: {exc}")
        _log_collection(symbol, "price", "error", str(exc))
        return 0


def ensure_up_to_date(symbol: str) -> int:
    """Fetch fresh data only when the latest stored date is not today.

    This is the on-demand entry-point called when a user queries a symbol.
    If data is already current (latest stored date == today) it does nothing.

    Returns:
        Number of rows upserted (0 if data was already current).
    """
    today = date.today()
    last_date = _last_stored_date(symbol)

    if last_date == today:
        logger.debug(f"[price] {symbol} is already up-to-date ({today}).")
        return 0

    start = (last_date + timedelta(days=1)) if last_date else None
    logger.info(
        f"[price] {symbol} needs update "
        f"(last={last_date or 'none'}, today={today})."
    )
    return fetch_prices(symbol, start=start)


def fetch_batch(symbols: list[str]) -> dict[str, int]:
    """Fetch / refresh a list of symbols sequentially.

    Returns a dict mapping each symbol to the number of rows upserted.
    """
    results: dict[str, int] = {}
    for symbol in symbols:
        results[symbol] = fetch_prices(symbol)
    return results

"""Technical indicator calculation and persistence for stock symbols."""

from datetime import date
from typing import Optional

import pandas as pd
from loguru import logger
from sqlalchemy import select
from ta.momentum import RSIIndicator
from ta.trend import MACD
from ta.volatility import BollingerBands

from db.models import StockPrice, TechnicalIndicator
from db.session import get_session


def calculate(symbol: str) -> int:
    """Calculate all technical indicators for *symbol* and upsert into DB.

    Reads all available closing prices for *symbol* from ``stock_prices``,
    computes MA5 / MA20 / MA60, RSI-14, MACD(12,26,9), and Bollinger Bands
    (20, ±2σ), then writes the results to ``technical_indicators``.

    Parameters
    ----------
    symbol:
        Ticker symbol, e.g. ``"AAPL"``, ``"2330.TW"``, ``"7203.T"``.

    Returns
    -------
    int
        Number of rows upserted.
    """
    with get_session() as session:
        rows = (
            session.execute(
                select(StockPrice.date, StockPrice.close)
                .where(StockPrice.symbol == symbol)
                .order_by(StockPrice.date)
            )
            .all()
        )

    if not rows:
        logger.warning(f"[{symbol}] No price data found; skipping indicator calculation.")
        return 0

    df = pd.DataFrame(rows, columns=["date", "close"]).set_index("date")
    close = df["close"].astype(float)

    df["ma5"] = close.rolling(5).mean()
    df["ma20"] = close.rolling(20).mean()
    df["ma60"] = close.rolling(60).mean()

    rsi = RSIIndicator(close=close, window=14)
    df["rsi14"] = rsi.rsi()

    macd_obj = MACD(close=close, window_slow=26, window_fast=12, window_sign=9)
    df["macd"] = macd_obj.macd()
    df["macd_signal"] = macd_obj.macd_signal()
    df["macd_hist"] = macd_obj.macd_diff()

    bb = BollingerBands(close=close, window=20, window_dev=2)
    df["bb_upper"] = bb.bollinger_hband()
    df["bb_middle"] = bb.bollinger_mavg()
    df["bb_lower"] = bb.bollinger_lband()

    def _float_or_none(val) -> Optional[float]:
        import math
        if val is None:
            return None
        try:
            f = float(val)
            return None if math.isnan(f) else f
        except (TypeError, ValueError):
            return None

    upserted = 0
    with get_session() as session:
        for row_date, row in df.iterrows():
            existing = session.execute(
                select(TechnicalIndicator).where(
                    TechnicalIndicator.symbol == symbol,
                    TechnicalIndicator.date == row_date,
                )
            ).scalar_one_or_none()

            values = dict(
                ma5=_float_or_none(row.get("ma5")),
                ma20=_float_or_none(row.get("ma20")),
                ma60=_float_or_none(row.get("ma60")),
                rsi14=_float_or_none(row.get("rsi14")),
                macd=_float_or_none(row.get("macd")),
                macd_signal=_float_or_none(row.get("macd_signal")),
                macd_hist=_float_or_none(row.get("macd_hist")),
                bb_upper=_float_or_none(row.get("bb_upper")),
                bb_middle=_float_or_none(row.get("bb_middle")),
                bb_lower=_float_or_none(row.get("bb_lower")),
            )

            if existing:
                for k, v in values.items():
                    setattr(existing, k, v)
            else:
                session.add(TechnicalIndicator(symbol=symbol, date=row_date, **values))

            upserted += 1

    logger.info(f"[{symbol}] Technical indicators upserted: {upserted} rows.")
    return upserted


def get(
    symbol: str,
    start_date: Optional[date] = None,
    end_date: Optional[date] = None,
) -> pd.DataFrame:
    """Retrieve pre-computed technical indicators from DB as a DataFrame.

    Parameters
    ----------
    symbol:
        Ticker symbol.
    start_date:
        Inclusive lower bound on ``date``.  ``None`` means no lower bound.
    end_date:
        Inclusive upper bound on ``date``.  ``None`` means no upper bound.

    Returns
    -------
    pd.DataFrame
        Columns: ``date``, ``ma5``, ``ma20``, ``ma60``, ``rsi14``,
        ``macd``, ``macd_signal``, ``macd_hist``,
        ``bb_upper``, ``bb_middle``, ``bb_lower``.
        Indexed by ``date`` (ascending).  Empty DataFrame if no data found.
    """
    stmt = select(TechnicalIndicator).where(TechnicalIndicator.symbol == symbol)
    if start_date is not None:
        stmt = stmt.where(TechnicalIndicator.date >= start_date)
    if end_date is not None:
        stmt = stmt.where(TechnicalIndicator.date <= end_date)
    stmt = stmt.order_by(TechnicalIndicator.date)

    with get_session() as session:
        rows = [
            {
                "date": r.date,
                "ma5": r.ma5,
                "ma20": r.ma20,
                "ma60": r.ma60,
                "rsi14": r.rsi14,
                "macd": r.macd,
                "macd_signal": r.macd_signal,
                "macd_hist": r.macd_hist,
                "bb_upper": r.bb_upper,
                "bb_middle": r.bb_middle,
                "bb_lower": r.bb_lower,
            }
            for r in session.execute(stmt).scalars().all()
        ]

    if not rows:
        logger.warning(f"[{symbol}] No technical indicator data found for the given range.")
        return pd.DataFrame()

    df = pd.DataFrame(rows).set_index("date")
    return df

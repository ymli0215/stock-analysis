"""Cross-market correlation analysis based on daily closing prices.

Reads closing prices from ``stock_prices``, aligns multi-market calendars
via outer-join + forward-fill, converts to daily returns, then computes
either a full correlation matrix or a rolling pairwise correlation.
"""

from datetime import date
from typing import Optional

import pandas as pd
from loguru import logger
from sqlalchemy import select

from db.models import StockPrice
from db.session import get_session


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------


def _load_closes(
    symbols: list[str],
    start_date: Optional[date],
    end_date: Optional[date],
) -> pd.DataFrame:
    """Query closing prices for *symbols* and return a wide DataFrame.

    Columns are symbol names; index is date (ascending).
    Rows are the *union* of all trading days (outer join), with NaN where a
    market was closed on a given day.

    Parameters
    ----------
    symbols:
        List of ticker symbols.
    start_date, end_date:
        Inclusive date bounds.  ``None`` means unbounded.
    """
    series: dict[str, pd.Series] = {}

    for symbol in symbols:
        stmt = (
            select(StockPrice.date, StockPrice.close)
            .where(StockPrice.symbol == symbol)
        )
        if start_date is not None:
            stmt = stmt.where(StockPrice.date >= start_date)
        if end_date is not None:
            stmt = stmt.where(StockPrice.date <= end_date)
        stmt = stmt.order_by(StockPrice.date)

        with get_session() as session:
            rows = session.execute(stmt).all()

        if not rows:
            logger.warning(f"[correlation] No price data for {symbol}; it will be excluded.")
            continue

        series[symbol] = pd.Series(
            {r.date: r.close for r in rows},
            name=symbol,
            dtype=float,
        )

    if not series:
        return pd.DataFrame()

    # Outer-join all series so every calendar day across markets is present
    df = pd.concat(series.values(), axis=1, join="outer")
    df.index = pd.to_datetime(df.index)
    df.sort_index(inplace=True)

    # Forward-fill to bridge non-overlapping market holidays (cap at 7 days to
    # avoid propagating stale prices across long gaps such as Golden Week)
    df.ffill(limit=7, inplace=True)

    return df


def _to_returns(df: pd.DataFrame) -> pd.DataFrame:
    """Convert a price DataFrame to daily percentage returns.

    The first row becomes NaN after pct_change and is dropped.
    """
    returns = df.pct_change()
    returns.dropna(how="all", inplace=True)
    return returns


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------


def calculate(
    symbols: list[str],
    start_date: Optional[date] = None,
    end_date: Optional[date] = None,
) -> pd.DataFrame:
    """Compute the pairwise correlation matrix for *symbols*.

    Closing prices are loaded from ``stock_prices``, aligned across markets
    via outer-join + forward-fill, then converted to daily returns before
    computing Pearson correlations.

    Parameters
    ----------
    symbols:
        Two or more ticker symbols from any supported market.
    start_date:
        Inclusive lower bound on the date range.  ``None`` means no bound.
    end_date:
        Inclusive upper bound on the date range.  ``None`` means no bound.

    Returns
    -------
    pd.DataFrame
        Square correlation matrix with symbol names as both index and columns.
        Returns an empty DataFrame when fewer than two symbols have data.
    """
    if len(symbols) < 2:
        logger.warning("[correlation] At least 2 symbols are required.")
        return pd.DataFrame()

    closes = _load_closes(symbols, start_date, end_date)
    if closes.shape[1] < 2:
        logger.warning("[correlation] Fewer than 2 symbols have data; cannot compute matrix.")
        return pd.DataFrame()

    returns = _to_returns(closes)

    # Drop symbols that ended up all-NaN after conversion
    returns.dropna(axis=1, how="all", inplace=True)
    if returns.shape[1] < 2:
        logger.warning("[correlation] Not enough return data to compute correlation.")
        return pd.DataFrame()

    corr = returns.corr(method="pearson")
    logger.info(
        f"[correlation] Matrix computed for {list(corr.columns)} "
        f"({len(returns)} return observations)."
    )
    return corr


def rolling_correlation(
    symbol_a: str,
    symbol_b: str,
    window: int = 30,
    start_date: Optional[date] = None,
    end_date: Optional[date] = None,
) -> pd.DataFrame:
    """Compute the rolling Pearson correlation between two symbols.

    Aligns the two price series via outer-join + forward-fill, converts to
    daily returns, then applies a rolling window correlation.

    Parameters
    ----------
    symbol_a, symbol_b:
        Ticker symbols to compare.
    window:
        Rolling window size in calendar days (default: 30).
    start_date, end_date:
        Optional date bounds applied when loading prices from DB.

    Returns
    -------
    pd.DataFrame
        Single-column DataFrame with column ``"correlation"`` and a
        ``DatetimeIndex``.  NaN rows at the start (before the first full
        window) are dropped.  Returns an empty DataFrame if data is
        insufficient.
    """
    closes = _load_closes([symbol_a, symbol_b], start_date, end_date)
    if closes.shape[1] < 2:
        logger.warning(
            f"[correlation] Could not load data for both {symbol_a} and {symbol_b}."
        )
        return pd.DataFrame()

    returns = _to_returns(closes)
    returns.dropna(how="all", inplace=True)

    if len(returns) < window:
        logger.warning(
            f"[correlation] Only {len(returns)} observations for "
            f"{symbol_a}/{symbol_b}, less than window={window}."
        )
        return pd.DataFrame()

    rolling_corr = (
        returns[symbol_a]
        .rolling(window=window, min_periods=window)
        .corr(returns[symbol_b])
    )

    result = rolling_corr.dropna().rename("correlation").to_frame()
    logger.info(
        f"[correlation] Rolling({window}) {symbol_a}↔{symbol_b}: "
        f"{len(result)} data points."
    )
    return result

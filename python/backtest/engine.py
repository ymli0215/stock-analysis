"""Vectorbt-based backtesting engine.

Supports two built-in strategies:

* ``"ma_cross"`` — buy when MA5 crosses above MA20, sell when it crosses below.
* ``"rsi"``       — buy when RSI-14 drops below 30, sell when it rises above 70.

Prices and pre-computed indicators are read directly from the DB so no
internet access is required at backtest time.
"""

from datetime import date
from typing import Any, Literal, Optional

import numpy as np
import pandas as pd
import vectorbt as vbt
from loguru import logger
from sqlalchemy import select

from db.models import StockPrice, TechnicalIndicator
from db.session import get_session

Strategy = Literal["ma_cross", "rsi"]

# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------


def _load_prices(symbol: str, start_date: Optional[date], end_date: Optional[date]) -> pd.Series:
    """Return a DatetimeIndex-ed closing-price Series for *symbol*."""
    stmt = (
        select(StockPrice.date, StockPrice.close)
        .where(StockPrice.symbol == symbol)
    )
    if start_date:
        stmt = stmt.where(StockPrice.date >= start_date)
    if end_date:
        stmt = stmt.where(StockPrice.date <= end_date)
    stmt = stmt.order_by(StockPrice.date)

    with get_session() as session:
        rows = session.execute(stmt).all()

    if not rows:
        return pd.Series(dtype=float)

    return pd.Series(
        {pd.Timestamp(r.date): float(r.close) for r in rows},
        name="close",
    )


def _load_indicators(
    symbol: str,
    start_date: Optional[date],
    end_date: Optional[date],
) -> pd.DataFrame:
    """Return a DatetimeIndex-ed DataFrame of technical indicators."""
    stmt = (
        select(TechnicalIndicator)
        .where(TechnicalIndicator.symbol == symbol)
    )
    if start_date:
        stmt = stmt.where(TechnicalIndicator.date >= start_date)
    if end_date:
        stmt = stmt.where(TechnicalIndicator.date <= end_date)
    stmt = stmt.order_by(TechnicalIndicator.date)

    with get_session() as session:
        records = session.execute(stmt).scalars().all()

    if not records:
        return pd.DataFrame()

    return pd.DataFrame(
        [
            {
                "date": pd.Timestamp(r.date),
                "ma5": r.ma5,
                "ma20": r.ma20,
                "rsi14": r.rsi14,
            }
            for r in records
        ]
    ).set_index("date").astype(float)


def _signals_ma_cross(ind: pd.DataFrame) -> tuple[pd.Series, pd.Series]:
    """MA5 / MA20 golden-cross / death-cross entry and exit signals."""
    ma5 = ind["ma5"]
    ma20 = ind["ma20"]

    above = ma5 > ma20
    # Entry: MA5 just crossed above MA20 (False → True)
    entries = (~above.shift(1).fillna(False)) & above
    # Exit:  MA5 just crossed below MA20 (True → False)
    exits = above.shift(1).fillna(False) & (~above)

    return entries, exits


def _signals_rsi(ind: pd.DataFrame) -> tuple[pd.Series, pd.Series]:
    """RSI oversold / overbought entry and exit signals."""
    rsi = ind["rsi14"]

    was_below_30 = rsi.shift(1) < 30
    # Entry: RSI recovers out of oversold (crosses back above 30)
    entries = was_below_30 & (rsi >= 30)

    was_above_70 = rsi.shift(1) > 70
    # Exit: RSI falls back from overbought (crosses back below 70)
    exits = was_above_70 & (rsi <= 70)

    return entries, exits


def _extract_trades(portfolio: vbt.Portfolio) -> list[dict]:
    """Convert vbt trade records to a plain list of dicts."""
    try:
        trades_df: pd.DataFrame = portfolio.trades.records_readable
    except Exception:
        return []

    if trades_df is None or trades_df.empty:
        return []

    result = []
    for _, row in trades_df.iterrows():
        trade: dict[str, Any] = {}
        for col in trades_df.columns:
            val = row[col]
            if isinstance(val, pd.Timestamp):
                trade[col] = val.date().isoformat()
            elif isinstance(val, float) and np.isnan(val):
                trade[col] = None
            else:
                trade[col] = val
        result.append(trade)
    return result


def _safe_float(val) -> Optional[float]:
    try:
        f = float(val)
        return None if (np.isnan(f) or np.isinf(f)) else f
    except (TypeError, ValueError):
        return None


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------


def run(
    symbol: str,
    strategy: Strategy,
    start_date: Optional[date] = None,
    end_date: Optional[date] = None,
    init_cash: float = 100_000.0,
    fees: float = 0.001425,
) -> dict:
    """Execute a backtest for *symbol* using the named *strategy*.

    Parameters
    ----------
    symbol:
        Ticker symbol (e.g. ``"AAPL"``, ``"2330.TW"``).
    strategy:
        ``"ma_cross"`` or ``"rsi"``.
    start_date, end_date:
        Inclusive date range.  ``None`` means unbounded.
    init_cash:
        Starting capital in the symbol's native currency (default: 100 000).
    fees:
        One-way commission rate (default: 0.1425 %, Taiwan stock rate).

    Returns
    -------
    dict
        Keys: ``total_return``, ``max_drawdown``, ``sharpe_ratio``,
        ``win_rate``, ``trades``.
        All numeric values are ``float | None``; ``trades`` is a list of dicts.
        On failure, returns a dict with all metrics as ``None`` and an
        ``"error"`` key describing the problem.
    """
    _SUPPORTED = ("ma_cross", "rsi")
    if strategy not in _SUPPORTED:
        return {
            "total_return": None, "max_drawdown": None,
            "sharpe_ratio": None, "win_rate": None, "trades": [],
            "error": f"Unknown strategy '{strategy}'. Supported: {_SUPPORTED}",
        }

    logger.info(f"[backtest] {symbol} | strategy={strategy} | {start_date} → {end_date}")

    # ── Load data ────────────────────────────────────────────────────────────
    closes = _load_prices(symbol, start_date, end_date)
    if closes.empty:
        msg = f"No price data in DB for {symbol}."
        logger.error(f"[backtest] {msg}")
        return {"total_return": None, "max_drawdown": None,
                "sharpe_ratio": None, "win_rate": None, "trades": [], "error": msg}

    ind = _load_indicators(symbol, start_date, end_date)
    if ind.empty:
        msg = f"No technical indicator data in DB for {symbol}. Run analysis.technical.calculate('{symbol}') first."
        logger.error(f"[backtest] {msg}")
        return {"total_return": None, "max_drawdown": None,
                "sharpe_ratio": None, "win_rate": None, "trades": [], "error": msg}

    # Align closes and indicators to the same index
    common_idx = closes.index.intersection(ind.index)
    if len(common_idx) < 2:
        msg = f"Insufficient aligned data for {symbol} ({len(common_idx)} rows)."
        logger.error(f"[backtest] {msg}")
        return {"total_return": None, "max_drawdown": None,
                "sharpe_ratio": None, "win_rate": None, "trades": [], "error": msg}

    closes = closes.loc[common_idx]
    ind = ind.loc[common_idx]

    # ── Generate signals ─────────────────────────────────────────────────────
    try:
        if strategy == "ma_cross":
            entries, exits = _signals_ma_cross(ind)
        else:
            entries, exits = _signals_rsi(ind)
    except KeyError as exc:
        msg = f"Missing indicator column for strategy '{strategy}': {exc}."
        logger.error(f"[backtest] {msg}")
        return {"total_return": None, "max_drawdown": None,
                "sharpe_ratio": None, "win_rate": None, "trades": [], "error": msg}

    # Ensure boolean dtype and same index as closes
    entries = entries.reindex(closes.index, fill_value=False).astype(bool)
    exits = exits.reindex(closes.index, fill_value=False).astype(bool)

    if not entries.any():
        msg = f"Strategy '{strategy}' produced no entry signals for {symbol}."
        logger.warning(f"[backtest] {msg}")
        return {"total_return": None, "max_drawdown": None,
                "sharpe_ratio": None, "win_rate": None, "trades": [], "error": msg}

    # ── Run vectorbt portfolio ────────────────────────────────────────────────
    try:
        portfolio = vbt.Portfolio.from_signals(
            close=closes,
            entries=entries,
            exits=exits,
            init_cash=init_cash,
            fees=fees,
            freq="D",
        )
    except Exception as exc:
        msg = f"vectorbt Portfolio.from_signals failed: {exc}"
        logger.error(f"[backtest] {msg}")
        return {"total_return": None, "max_drawdown": None,
                "sharpe_ratio": None, "win_rate": None, "trades": [], "error": msg}

    # ── Extract metrics ───────────────────────────────────────────────────────
    try:
        total_return = _safe_float(portfolio.total_return())
        max_drawdown = _safe_float(portfolio.max_drawdown())
        sharpe_ratio = _safe_float(portfolio.sharpe_ratio())

        trades_df = portfolio.trades.records_readable
        if trades_df is not None and not trades_df.empty:
            # Normalise possible column name variants across vbt versions
            pnl_col = next(
                (c for c in trades_df.columns if "pnl" in c.lower() or "profit" in c.lower()),
                None,
            )
            if pnl_col:
                n_wins = int((trades_df[pnl_col] > 0).sum())
                n_total = len(trades_df)
                win_rate = _safe_float(n_wins / n_total if n_total else 0.0)
            else:
                win_rate = None
        else:
            win_rate = None

        trades = _extract_trades(portfolio)

    except Exception as exc:
        msg = f"Failed to extract metrics: {exc}"
        logger.error(f"[backtest] {msg}")
        return {"total_return": None, "max_drawdown": None,
                "sharpe_ratio": None, "win_rate": None, "trades": [], "error": msg}

    result = {
        "total_return": total_return,
        "max_drawdown": max_drawdown,
        "sharpe_ratio": sharpe_ratio,
        "win_rate": win_rate,
        "trades": trades,
    }

    logger.info(
        f"[backtest] {symbol} {strategy} done — "
        f"return={total_return:.2%} drawdown={max_drawdown:.2%} "
        f"sharpe={sharpe_ratio} win_rate={win_rate}"
    )
    return result

"""collector/fundamental.py — fetch and store fundamental data per stock.

Writes to the stock_fundamental table in the stockapp DB.
- SNAP row  : today's valuation snapshot (PE, PB, ROE, divYield, EPS)
- Q1~Q4 rows: quarterly income + balance-sheet figures
"""

from __future__ import annotations

import math
from datetime import date, datetime
from typing import Optional

import os
from pathlib import Path

import pymysql
import yfinance as yf
from dotenv import load_dotenv
from loguru import logger

# Override env vars with .env so this module always writes to stockapp,
# regardless of what DB_NAME the container OS environment has set.
load_dotenv(Path(__file__).parent.parent / ".env", override=True)


def _get_conn() -> pymysql.connections.Connection:
    """pymysql connection to the stockapp DB (reads from .env with override)."""
    return pymysql.connect(
        host=os.environ["DB_HOST"],
        port=int(os.environ.get("DB_PORT", 3306)),
        user=os.environ["DB_USER"],
        password=os.environ["DB_PASSWORD"],
        database=os.environ["DB_NAME"],
        charset="utf8mb4",
        autocommit=False,
    )

# ── helpers ────────────────────────────────────────────────────────────────────

def _f(val) -> Optional[float]:
    """Cast val to float; return None for None / NaN / errors."""
    try:
        v = float(val)
        return None if math.isnan(v) else v
    except (TypeError, ValueError):
        return None


def _quarter(dt) -> str:
    """Return Q1/Q2/Q3/Q4 from a date-like object (calendar quarter)."""
    month = dt.month if hasattr(dt, "month") else datetime.fromisoformat(str(dt)).month
    return ["Q1", "Q2", "Q3", "Q4"][(month - 1) // 3]


# Canonical quarter-end dates: (month, day)
_QUARTER_END = {1: (3, 31), 2: (6, 30), 3: (9, 30), 4: (12, 31)}

def _normalize_fiscal_date(dt: date) -> date:
    """Snap any date to the canonical quarter-end date for its calendar quarter.

    Prevents duplicate rows when yfinance returns slightly off dates
    (e.g. 2024-09-28 instead of 2024-09-30) for the same quarter.
    """
    q_idx = (dt.month - 1) // 3 + 1  # 1-based quarter index
    end_month, end_day = _QUARTER_END[q_idx]
    return dt.replace(month=end_month, day=end_day)


# ── SQL ────────────────────────────────────────────────────────────────────────

_UPSERT = """
    INSERT INTO stock_fundamental
        (stockId, market, fiscalDate, period,
         revenue, netIncome, eps, totalAssets, totalDebt,
         pe, pb, roe, divYield)
    VALUES
        (%(stockId)s, %(market)s, %(fiscalDate)s, %(period)s,
         %(revenue)s, %(netIncome)s, %(eps)s, %(totalAssets)s, %(totalDebt)s,
         %(pe)s, %(pb)s, %(roe)s, %(divYield)s)
    ON DUPLICATE KEY UPDATE
        revenue    = VALUES(revenue),
        netIncome  = VALUES(netIncome),
        eps        = VALUES(eps),
        totalAssets= VALUES(totalAssets),
        totalDebt  = VALUES(totalDebt),
        pe         = VALUES(pe),
        pb         = VALUES(pb),
        roe        = VALUES(roe),
        divYield   = VALUES(divYield)
"""

# ── main function ──────────────────────────────────────────────────────────────

def fetch_fundamental(stock_id: str, market: str) -> dict:
    """Fetch fundamental data for one stock and upsert into stock_fundamental.

    Args:
        stock_id: ticker as stored in DB (e.g. '7203.T', '2330.TW', 'AAPL')
        market:   market code — 'JP', 'TW', or 'US'

    Returns:
        dict(status, snap_written, quarterly_written, message)
    """
    rows: list[dict] = []

    try:
        ticker = yf.Ticker(stock_id)

        # ── 1. Valuation snapshot (period='SNAP', fiscalDate=today) ────────────
        try:
            info = ticker.info
            rows.append({
                "stockId":     stock_id,
                "market":      market,
                "fiscalDate":  date.today().isoformat(),
                "period":      "SNAP",
                "revenue":     None,
                "netIncome":   None,
                "eps":         _f(info.get("trailingEps")),
                "totalAssets": None,
                "totalDebt":   None,
                "pe":          _f(info.get("trailingPE")),
                "pb":          _f(info.get("priceToBook")),
                "roe":         _f(info.get("returnOnEquity")),
                "divYield":    _f(info.get("dividendYield")),
            })
        except Exception as exc:
            logger.warning("[{}] info fetch skipped: {}", stock_id, exc)

        # ── 2. Quarterly financials (period='Q1'~'Q4') ─────────────────────────
        try:
            qf  = ticker.quarterly_financials
            qbs = ticker.quarterly_balance_sheet

            # Merge column dates from both frames
            col_dates: set = set()
            if not qf.empty:
                col_dates.update(qf.columns.tolist())
            if not qbs.empty:
                col_dates.update(qbs.columns.tolist())

            for col in sorted(col_dates, reverse=True):
                def _get(df, *keys):
                    if df.empty or col not in df.columns:
                        return None
                    for k in keys:
                        if k in df.index:
                            return _f(df.loc[k, col])
                    return None

                revenue     = _get(qf,  "Total Revenue", "Revenue")
                net_income  = _get(qf,  "Net Income", "Net Income Common Stockholders")
                total_assets= _get(qbs, "Total Assets")
                total_debt  = _get(qbs, "Total Debt", "Long Term Debt")

                # Skip entirely empty rows
                if all(v is None for v in (revenue, net_income, total_assets, total_debt)):
                    continue

                raw_date    = col.date() if hasattr(col, "date") else col
                fiscal_date = _normalize_fiscal_date(raw_date)

                rows.append({
                    "stockId":     stock_id,
                    "market":      market,
                    "fiscalDate":  fiscal_date.isoformat(),
                    "period":      _quarter(fiscal_date),
                    "revenue":     revenue,
                    "netIncome":   net_income,
                    "eps":         None,
                    "totalAssets": total_assets,
                    "totalDebt":   total_debt,
                    "pe":          None,
                    "pb":          None,
                    "roe":         None,
                    "divYield":    None,
                })
        except Exception as exc:
            logger.warning("[{}] quarterly data skipped: {}", stock_id, exc)

        # ── 3. Write ──────────────────────────────────────────────────────────
        if not rows:
            return {"status": "ok", "snap_written": 0, "quarterly_written": 0,
                    "message": "no data"}

        conn = _get_conn()
        try:
            with conn.cursor() as cur:
                cur.executemany(_UPSERT, rows)
            conn.commit()
        finally:
            conn.close()

        snap_n = sum(1 for r in rows if r["period"] == "SNAP")
        qtr_n  = len(rows) - snap_n
        logger.info("[{}] upserted snap={} quarterly={}", stock_id, snap_n, qtr_n)
        return {"status": "ok", "snap_written": snap_n, "quarterly_written": qtr_n,
                "message": f"{len(rows)} rows upserted"}

    except Exception as exc:
        logger.error("[{}] fetch_fundamental error: {}", stock_id, exc)
        return {"status": "error", "snap_written": 0, "quarterly_written": 0,
                "message": str(exc)}

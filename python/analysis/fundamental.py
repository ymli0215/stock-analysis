"""Fundamental data fetcher using Yahoo Finance quoteSummary API directly.

Fetches EPS, PE/PB ratio, ROE, revenue, net income, and dividend yield
via the Yahoo Finance v10 quoteSummary endpoint (same cookie / crumb flow
as the price collector) and stores results in the ``fundamentals`` table.

yfinance's internal CrumbManager is bypassed because it fails with 429 in
environments where the raw session works fine.
"""

import math
import os
from datetime import date, datetime
from typing import Optional

from dotenv import load_dotenv
from loguru import logger
from sqlalchemy import select

from db.models import CollectorLog, Fundamental
from db.session import get_session

load_dotenv()

# ---------------------------------------------------------------------------
# Internal helpers (mirror collector/price.py style)
# ---------------------------------------------------------------------------

_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36",
    "Accept": "*/*",
    "Accept-Language": "zh-TW,zh;q=0.9,en;q=0.8",
    "Accept-Encoding": "gzip, deflate, br",
    "Referer": "https://finance.yahoo.com/",
    "Origin": "https://finance.yahoo.com",
    "sec-ch-ua": '"Google Chrome";v="147", "Not.A/Brand";v="8", "Chromium";v="147"',
    "sec-ch-ua-mobile": "?0",
    "sec-ch-ua-platform": '"Windows"',
    "sec-fetch-dest": "empty",
    "sec-fetch-mode": "cors",
    "sec-fetch-site": "same-site",
}


def _build_requests_session():
    """Return a requests.Session with browser headers and YAHOO_COOKIE applied."""
    import requests

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


def _get_crumb(session) -> str:
    """Fetch a valid Yahoo Finance crumb using *session*.

    Returns empty string on failure (requests will still work for many
    endpoints without a crumb, but quoteSummary requires one).
    """
    try:
        r = session.get(
            "https://query1.finance.yahoo.com/v1/test/getcrumb",
            timeout=15,
        )
        if r.ok and r.text.strip():
            return r.text.strip()
    except Exception as exc:
        logger.warning(f"[fundamental] crumb fetch failed: {exc}")
    return ""


def _fetch_quote_summary(symbol: str, session, crumb: str) -> dict:
    """Call Yahoo Finance v10 quoteSummary and return the result dict.

    Modules fetched: defaultKeyStatistics, financialData, summaryDetail,
    incomeStatementHistory, incomeStatementHistoryQuarterly.
    """
    modules = ",".join([
        "defaultKeyStatistics",
        "financialData",
        "summaryDetail",
        "incomeStatementHistory",
        "incomeStatementHistoryQuarterly",
    ])
    url = f"https://query1.finance.yahoo.com/v10/finance/quoteSummary/{symbol}"
    params = {
        "modules": modules,
        "crumb": crumb,
        "formatted": "false",
        "lang": "en-US",
        "region": "US",
    }
    resp = session.get(url, params=params, timeout=30)
    resp.raise_for_status()
    data = resp.json()
    results = data.get("quoteSummary", {}).get("result") or []
    if not results:
        error = data.get("quoteSummary", {}).get("error")
        raise ValueError(f"quoteSummary returned no result for {symbol}: {error}")
    return results[0]


def _safe_float(value) -> Optional[float]:
    """Return float or None; treats NaN / inf as None."""
    if value is None:
        return None
    try:
        # Yahoo returns plain numbers when formatted=false
        f = float(value) if not isinstance(value, dict) else float(value.get("raw", "nan"))
        return None if (math.isnan(f) or math.isinf(f)) else f
    except (TypeError, ValueError):
        return None


def _parse_unix_date(val) -> Optional[date]:
    """Convert a Yahoo unix-timestamp field to a Python date."""
    try:
        raw = val if not isinstance(val, dict) else val.get("raw", val)
        return date.fromtimestamp(int(raw))
    except (TypeError, ValueError, OSError):
        return None


def _log_collection(
    symbol: str,
    status: str,
    message: str = "",
    rows_upserted: int = 0,
) -> None:
    with get_session() as session:
        session.add(
            CollectorLog(
                symbol=symbol,
                collector_type="fundamental",
                status=status,
                message=message,
                rows_upserted=rows_upserted,
            )
        )


def _upsert_fundamental(session, symbol: str, period: date, period_type: str, **kwargs) -> bool:
    """Upsert a single Fundamental row; return True if inserted, False if updated."""
    record = session.execute(
        select(Fundamental).where(
            Fundamental.symbol == symbol,
            Fundamental.period == period,
            Fundamental.period_type == period_type,
        )
    ).scalar_one_or_none()

    if record is None:
        record = Fundamental(symbol=symbol, period=period, period_type=period_type)
        session.add(record)
        inserted = True
    else:
        inserted = False

    for key, val in kwargs.items():
        if val is not None:
            setattr(record, key, val)

    record.updated_at = datetime.utcnow()
    return inserted


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------


def fetch_and_save(symbol: str) -> int:
    """Fetch fundamental data from Yahoo Finance quoteSummary and upsert into DB.

    Calls the v10/finance/quoteSummary endpoint directly (bypassing yfinance's
    CrumbManager, which fails with 429 in this environment) using the same
    cookie / headers as the price collector.

    Persists annual and quarterly income-statement rows (``period_type`` =
    ``'A'`` / ``'Q'``) plus trailing valuation metrics attached to the most
    recent annual period.

    Parameters
    ----------
    symbol:
        Ticker symbol, e.g. ``"AAPL"``, ``"2330.TW"``, ``"7203.T"``.

    Returns
    -------
    int
        Number of rows upserted (0 on failure).
    """
    logger.info(f"[fundamental] Fetching {symbol}")
    try:
        http = _build_requests_session()
        crumb = _get_crumb(http)
        summary = _fetch_quote_summary(symbol, http, crumb)

        # ── Snapshot metrics ─────────────────────────────────────────────────
        ks = summary.get("defaultKeyStatistics") or {}
        fd = summary.get("financialData") or {}
        sd = summary.get("summaryDetail") or {}

        trailing_eps    = _safe_float(ks.get("trailingEps"))
        pb_ratio        = _safe_float(ks.get("priceToBook"))
        debt_to_equity  = _safe_float(ks.get("debtToEquity"))
        trailing_pe     = _safe_float(sd.get("trailingPE"))
        dividend_yield  = _safe_float(sd.get("dividendYield"))
        roe             = _safe_float(fd.get("returnOnEquity"))

        # ── Income statement helper ──────────────────────────────────────────
        def _parse_income_list(items: list) -> dict[date, dict]:
            """Parse a list of incomeStatement dicts into {date: {revenue, net_income}}."""
            out: dict[date, dict] = {}
            for item in items:
                period_date = _parse_unix_date(item.get("endDate"))
                if period_date is None:
                    continue
                out[period_date] = {
                    "revenue":    _safe_float(item.get("totalRevenue")),
                    "net_income": _safe_float(item.get("netIncome")),
                }
            return out

        annual_data = _parse_income_list(
            (summary.get("incomeStatementHistory") or {})
            .get("incomeStatementHistory", [])
        )
        quarterly_data = _parse_income_list(
            (summary.get("incomeStatementHistoryQuarterly") or {})
            .get("incomeStatementHistory", [])
        )

        # ── Upsert ───────────────────────────────────────────────────────────
        # Trailing valuation metrics are injected into the MOST RECENT annual
        # period inside the same loop iteration to avoid a second
        # _upsert_fundamental call on the same (symbol, period, type) key —
        # which would cause a duplicate-key INSERT because autoflush=False
        # means the pending row is not yet visible to a subsequent SELECT.
        rows_upserted = 0
        annual_items = sorted(annual_data.items())
        most_recent_annual: Optional[date] = annual_items[-1][0] if annual_items else None

        trailing_vals = dict(
            eps=trailing_eps,
            pe_ratio=trailing_pe,
            pb_ratio=pb_ratio,
            roe=roe,
            dividend_yield=dividend_yield,
            debt_to_equity=debt_to_equity,
        )

        with get_session() as session:
            for period_date, metrics in annual_items:
                extra = trailing_vals if period_date == most_recent_annual else {}
                _upsert_fundamental(
                    session, symbol, period_date, "A",
                    revenue=metrics["revenue"],
                    net_income=metrics["net_income"],
                    **extra,
                )
                rows_upserted += 1

            for period_date, metrics in sorted(quarterly_data.items()):
                _upsert_fundamental(
                    session, symbol, period_date, "Q",
                    revenue=metrics["revenue"],
                    net_income=metrics["net_income"],
                )
                rows_upserted += 1

            # Fallback: save snapshot row even if no income-statement data
            if rows_upserted == 0:
                any_metric = any(v is not None for v in trailing_vals.values())
                if any_metric:
                    _upsert_fundamental(
                        session, symbol, date.today(), "A",
                        **trailing_vals,
                    )
                    rows_upserted += 1

        _log_collection(symbol, "ok", rows_upserted=rows_upserted)
        logger.info(f"[fundamental] {symbol}: {rows_upserted} rows upserted.")
        return rows_upserted

    except Exception as exc:
        logger.error(f"[fundamental] Failed to fetch {symbol}: {exc}")
        _log_collection(symbol, "error", str(exc))
        return 0


def get(symbol: str) -> dict:
    """Read fundamental data for *symbol* from DB and return as a dict.

    The returned dict has two keys:

    * ``"latest_annual"`` — most recent annual record as a flat dict
      (or ``{}`` if not found).
    * ``"quarterly"`` — list of quarterly records ordered newest-first,
      each as a flat dict.

    Parameters
    ----------
    symbol:
        Ticker symbol.

    Returns
    -------
    dict
        ``{"latest_annual": {...}, "quarterly": [{...}, ...]}``
    """
    def _to_dict(r) -> dict:
        return {
            "symbol": r.symbol,
            "period": r.period.isoformat() if r.period else None,
            "period_type": r.period_type,
            "revenue": r.revenue,
            "net_income": r.net_income,
            "eps": r.eps,
            "pe_ratio": r.pe_ratio,
            "pb_ratio": r.pb_ratio,
            "dividend_yield": r.dividend_yield,
            "roe": r.roe,
            "debt_to_equity": r.debt_to_equity,
            "updated_at": r.updated_at.isoformat() if r.updated_at else None,
        }

    with get_session() as session:
        annual_dicts = [
            _to_dict(r)
            for r in session.execute(
                select(Fundamental)
                .where(Fundamental.symbol == symbol, Fundamental.period_type == "A")
                .order_by(Fundamental.period.desc())
            ).scalars().all()
        ]
        quarterly_dicts = [
            _to_dict(r)
            for r in session.execute(
                select(Fundamental)
                .where(Fundamental.symbol == symbol, Fundamental.period_type == "Q")
                .order_by(Fundamental.period.desc())
            ).scalars().all()
        ]

    if not annual_dicts and not quarterly_dicts:
        logger.warning(f"[fundamental] No data found in DB for {symbol}.")

    return {
        "latest_annual": annual_dicts[0] if annual_dicts else {},
        "quarterly": quarterly_dicts,
    }

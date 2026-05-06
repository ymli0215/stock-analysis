"""APScheduler-based post-market data refresh for TW, JP, and US markets.

Schedule (all times Asia/Taipei, UTC+8):
  TW  — 14:00  (30 min after market close at 13:30)
  JP  — 15:30  (30 min after market close at 15:00)
  US  — 06:00  (next-day Taiwan time; ~17:00 US/Eastern previous day)

Each job queries stock_info for all tracked symbols in that market and calls
ensure_up_to_date() on each one sequentially.
"""

from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from loguru import logger
from pytz import timezone
from sqlalchemy import select

from db.models import StockInfo
from db.session import get_session

_TZ = timezone("Asia/Taipei")

# Module-level scheduler instance so main.py can start/stop it
scheduler = BackgroundScheduler(timezone=_TZ)


def _symbols_for_market(market: str) -> list[str]:
    """Return all tracked symbols for the given market tag (TW / JP / US)."""
    with get_session() as session:
        rows = session.execute(
            select(StockInfo.symbol).where(StockInfo.market == market)
        ).all()
    return [row[0] for row in rows]


def _run_market_update(market: str) -> None:
    """Fetch latest prices for every tracked symbol in *market*."""
    # Import here to avoid circular import at module load time
    from collector.price import ensure_up_to_date

    symbols = _symbols_for_market(market)
    if not symbols:
        logger.info(f"[scheduler] {market}: no symbols tracked, skipping.")
        return

    logger.info(f"[scheduler] {market}: starting update for {len(symbols)} symbol(s).")
    ok = err = 0
    for symbol in symbols:
        try:
            rows = ensure_up_to_date(symbol)
            if rows > 0:
                ok += 1
        except Exception as exc:
            logger.error(f"[scheduler] {market} update failed for {symbol}: {exc}")
            err += 1

    logger.info(f"[scheduler] {market}: done — {ok} updated, {err} errors.")


def _update_tw() -> None:
    _run_market_update("TW")


def _update_jp() -> None:
    _run_market_update("JP")


def _update_us() -> None:
    _run_market_update("US")


def init_scheduler() -> None:
    """Register all cron jobs and start the background scheduler.

    Safe to call multiple times — jobs are only added once.
    """
    if scheduler.running:
        logger.debug("[scheduler] Already running, skipping init.")
        return

    scheduler.add_job(
        _update_tw,
        trigger=CronTrigger(hour=14, minute=0, timezone=_TZ),
        id="update_tw",
        name="TW post-market update",
        replace_existing=True,
        misfire_grace_time=600,
    )
    scheduler.add_job(
        _update_jp,
        trigger=CronTrigger(hour=15, minute=30, timezone=_TZ),
        id="update_jp",
        name="JP post-market update",
        replace_existing=True,
        misfire_grace_time=600,
    )
    scheduler.add_job(
        _update_us,
        trigger=CronTrigger(hour=6, minute=0, timezone=_TZ),
        id="update_us",
        name="US post-market update",
        replace_existing=True,
        misfire_grace_time=600,
    )

    scheduler.start()
    logger.info(
        "[scheduler] Started — TW@14:00, JP@15:30, US@06:00 (Asia/Taipei)"
    )


def shutdown_scheduler() -> None:
    """Gracefully stop the scheduler (called on app teardown)."""
    if scheduler.running:
        scheduler.shutdown(wait=False)
        logger.info("[scheduler] Stopped.")

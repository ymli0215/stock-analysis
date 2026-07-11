"""StockServer 維運排程（原 n8n flow「Sync Stock Data」208JRIcBB1BiXZj9 與
「每日更新上市櫃權證資料」pC2cOkmx1c4389IN 移植）。

updateStockData 會觸發 StockServer 對外部資料源的爬蟲，單次可能執行很久，
timeout 放寬到 1 小時；不可作為 healthcheck 或高頻呼叫。
"""

import logging

import requests

import config
from services import telegram

logger = logging.getLogger(__name__)


def _call_stockserver(path: str, timeout: int) -> str:
    url = f"{config.STOCKSERVER_BASE}{path}"
    logger.info("呼叫 StockServer：%s", url)
    resp = requests.get(url, timeout=timeout)
    resp.raise_for_status()
    return resp.text[:200]


def sync_stock_data() -> dict:
    """每週六 06:00：依序更新日/週/月 K 線資料（爬蟲，勿隨意手動觸發）。"""
    chat = config.NOTIFY_GROUP_CHAT_ID
    telegram.send_message(chat, "開始更新股票資料", token=config.TELEGRAM_NOTIFY_BOT_TOKEN)
    results = {}
    try:
        for data_type in ("D", "W", "M"):
            results[data_type] = _call_stockserver(
                f"/stock/updateStockData?dc=40&si=&dataType={data_type}"
                "&startid=1000&endid=9999&beforedate=&callback=jsonp",
                timeout=3600,
            )
    except Exception as e:
        telegram.send_message(chat, f"❌ 股票資料更新失敗：{e}", token=config.TELEGRAM_NOTIFY_BOT_TOKEN)
        raise
    telegram.send_message(chat, "股票資料已更新完畢", token=config.TELEGRAM_NOTIFY_BOT_TOKEN)
    return {"status": "ok", "results": results}


def import_warrant() -> dict:
    """週一至週五 07:00：更新上市櫃權證資料。"""
    try:
        body = _call_stockserver("/stockwants/importWarrant", timeout=1800)
    except Exception as e:
        telegram.send_message(config.NOTIFY_GROUP_CHAT_ID, f"❌ 權證資料更新失敗：{e}", token=config.TELEGRAM_NOTIFY_BOT_TOKEN)
        raise
    return {"status": "ok", "response": body}

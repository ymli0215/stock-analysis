import logging

import requests

import config

logger = logging.getLogger(__name__)


def send_message(chat_id, text: str, token: str | None = None) -> None:
    token = token or config.TELEGRAM_BOT_TOKEN
    if not token:
        logger.error("TELEGRAM_BOT_TOKEN 未設定，無法發送訊息")
        return

    url = f"{config.TELEGRAM_API_BASE}/bot{token}/sendMessage"
    limit = config.TELEGRAM_MESSAGE_LIMIT
    chunks = [text[i : i + limit] for i in range(0, len(text), limit)] or [""]

    for chunk in chunks:
        try:
            resp = requests.post(url, json={"chat_id": chat_id, "text": chunk}, timeout=30)
            resp.raise_for_status()
        except requests.RequestException:
            logger.exception("發送 Telegram 訊息失敗 chat_id=%s", chat_id)


def download_file(file_id: str) -> bytes | None:
    """透過 getFile 下載 Telegram 檔案；失敗回傳 None。"""
    base = f"{config.TELEGRAM_API_BASE}/bot{config.TELEGRAM_BOT_TOKEN}"
    try:
        resp = requests.get(f"{base}/getFile", params={"file_id": file_id}, timeout=30)
        if not resp.ok:
            logger.error("getFile 失敗 file_id=%s: HTTP %s %s", file_id, resp.status_code, resp.text[:300])
            return None
        result = resp.json()["result"]
        file_size = result.get("file_size")
        if file_size and file_size > config.TELEGRAM_MAX_DOWNLOAD_BYTES:
            logger.error("檔案過大 file_id=%s size=%s，超過 Bot API 20MB 上限", file_id, file_size)
            return None
        file_path = result["file_path"]
        file_resp = requests.get(
            f"{config.TELEGRAM_API_BASE}/file/bot{config.TELEGRAM_BOT_TOKEN}/{file_path}",
            timeout=120,
        )
        file_resp.raise_for_status()
        return file_resp.content
    except Exception:
        logger.exception("下載 Telegram 檔案失敗 file_id=%s", file_id)
        return None

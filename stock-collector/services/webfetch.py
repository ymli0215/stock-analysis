import logging

import requests

import config

logger = logging.getLogger(__name__)

_JINA_HEADERS = {
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
    "Accept-Language": "zh-TW,zh;q=0.9,en;q=0.8,en-US;q=0.7,ja;q=0.6",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36",
}


def fetch_url_content(url: str) -> str:
    """經 r.jina.ai 擷取網頁內容。

    Jina 有時回 403 但錯誤 body 內其實帶有擷取結果（含 Title: / URL Source:），
    這種情況要把 body 當成功結果使用。
    """
    try:
        resp = requests.get(f"https://r.jina.ai/{url}", headers=_JINA_HEADERS, timeout=90)
        content = resp.text
        if not resp.ok and not ("Title:" in content or "URL Source:" in content):
            logger.warning("擷取網址失敗 %s: HTTP %s", url, resp.status_code)
            content = f"⚠️ [系統提示：讀取網址發生錯誤 ({url})]\n🛑 真實錯誤細節: HTTP {resp.status_code} {content[:500]}"
    except Exception as e:
        logger.exception("擷取網址失敗 %s", url)
        content = f"⚠️ [系統提示：讀取網址發生錯誤 ({url})]\n🛑 真實錯誤細節: {e}"

    if len(content) > config.JINA_MAX_LENGTH:
        content = content[: config.JINA_MAX_LENGTH] + "\n...[字數過長，已自動截斷保護]..."
    return content


def build_url_block(url: str) -> str:
    content = fetch_url_content(url)
    is_youtube = "youtube.com" in url or "youtu.be" in url
    label = "📺 === YouTube 影片逐字稿/資訊 ===" if is_youtube else "🔗 === 網頁擷取內容 ==="
    return f"\n\n{label}\n{content}\n========================\n"

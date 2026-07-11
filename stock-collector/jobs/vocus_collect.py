"""Vocus 付費沙龍文章收集分析（取代 n8n flow「Get Vocus-摩股雙週報」a0nASk4JksapafXd，
以及同源的「Get Vocus-邏輯投資」TQYmb4Ahut7FGAY1）。

設計（選項 A：爬蟲層正規化）：
  登入 → 逐沙龍抓清單 → 以 vocus_sync_log 去重 → 逐篇：
  取詳情（吸收 post/article 差異）→ 下載圖片到 Obsidian Assets →
  組正規化 markdown（正文 + 問答對話）→ claude CLI 分析（知識索引與分析專家）→
  組最終筆記寫 /obsidian/01_Sources → Telegram 通知 → 寫入 vocus_sync_log。

多沙龍以 config.VOCUS_SALONS 設定驅動；新增沙龍只要加一筆設定。
圖片在爬蟲階段就用 token 下載完成，下游不需再碰 vocus 權限——與 investanchors 未來 Python 化共用同一種「已正規化」輸出。
"""

import logging
import re
import time
from datetime import datetime

import requests

import config
from services import db, llm, obsidian, telegram, vocus

logger = logging.getLogger(__name__)

ANALYSIS_PROMPT = """# Role
你是一位專業的「知識索引與分析專家」。你的任務是讀取原始文章內容，產出易於人類閱讀的摘要，並根據「四色建模法」為文章標註關鍵的「知識索引 (Wiki Indexing)」。

# Task
請針對以下文章內容進行分析，並嚴格遵守輸出格式要求。

## 輸出格式要求
請直接使用 Obsidian 的 Callout 格式 ( > [!abstract]) 輸出，不要輸出任何 callout 以外的說明文字或程式碼區塊標記。

> [!abstract] 核心摘要與 Wiki 索引標籤
> 1. **💡 核心投資邏輯**：用一句話總結本文核心價值，並列出 3 個關鍵成長動能。
> 2. **⚠️ 潛在風險提示**：找出文章中提到的營運、產業或週期性風險。
> 3. **🎯 核心標的與技術 (Entities)**：列出文中提到的公司與核心技術，並一律使用 `[[雙向連結]]` 標註，以便未來索引。例如：`[[威剛]]`, `[[HBM]]`, `[[DDR5]]`
> 4. **🧠 知識索引提取 (Wiki Indexing)**：根據「四色建模法」，找出文中值得沉澱至 **02_Wiki** 的線索：
>    - **[[Moment-interval]] (事件)**：具有時效性的市場變化或預測。
>    - **[[Description]] (定義)**：不隨時間改變的技術定義、產業邏輯或商業模式。

---
# 文章內容：
{content}"""


def already_synced(article_id: str) -> bool:
    rows = db.fetch_all(
        "SELECT 1 FROM vocus_sync_log WHERE article_id = %s AND sync_status = 'COMPLETED' LIMIT 1",
        (article_id,),
    )
    return bool(rows)


def mark_synced(detail: dict) -> None:
    db.execute(
        """
        INSERT INTO vocus_sync_log (article_id, creator_id, title, sync_status, last_synced_at)
        VALUES (%s, %s, %s, 'COMPLETED', NOW())
        ON DUPLICATE KEY UPDATE creator_id=VALUES(creator_id), title=VALUES(title),
            sync_status='COMPLETED', last_synced_at=NOW()
        """,
        (detail["article_id"], detail["creator_id"], detail["title"]),
    )


def download_images(client: vocus.VocusClient, images: list[dict]) -> int:
    ok = 0
    for i, img in enumerate(images):
        if i > 0:
            time.sleep(0.5)
        try:
            r = client.s.get(img["url"], timeout=60)
            r.raise_for_status()
            obsidian.write_attachment(img["filename"], r.content)
            ok += 1
        except Exception:
            logger.exception("圖片下載失敗：%s", img["url"])
    return ok


def render_comments(comments: list[dict]) -> str:
    if not comments:
        return ""
    lines = ["", "---", "", "## 💬 問答對話", ""]
    for c in comments:
        like = f"（讚 {c['like']}）" if c.get("like") else ""
        lines.append(f"> **{c['author']}**{like}")
        for ln in (c["text"] or "").split("\n"):
            lines.append(f"> {ln}")
        for rp in c.get("replies", []):
            lines.append(">")
            lines.append(f"> └ **{rp['author']}**：{rp['text']}")
        lines.append("")
    return "\n".join(lines)


def build_frontmatter(detail: dict) -> str:
    tags = [re.sub(r"\s+", "_", str(t)) for t in detail.get("tags", []) if t]
    date_str = _fmt_date(detail["publish_at"])
    return (
        "---\n"
        f'title: "{detail["title"]}"\n'
        f'url: "{detail["url"]}"\n'
        f'author: "{detail["author"]}"\n'
        f'salon: "{detail["salon_name"]}"\n'
        f"date: {date_str}\n"
        f"tags: [{', '.join(tags)}]\n"
        "source: vocus\n"
        "---"
    )


def _fmt_date(raw: str) -> str:
    if not raw:
        return datetime.now().strftime("%Y-%m-%d")
    try:
        return datetime.fromisoformat(raw.replace("Z", "+00:00")).strftime("%Y-%m-%d")
    except (ValueError, AttributeError):
        return str(raw)[:10]


def process_article(client: vocus.VocusClient, meta: dict, salon_name: str) -> dict:
    detail = client.fetch_detail(meta, salon_name)
    # 留言 endpoint 用原始 type（post/article，單數），與詳情端點的 posts 不同
    detail["comments"] = client.fetch_comments(detail["type"], detail["article_id"])

    img_ok = download_images(client, detail["images"])

    # 正文（給 AI 分析 + 存原文）
    body = detail["markdown"]
    comments_md = render_comments(detail["comments"])

    ai_analysis = llm.run_claude(ANALYSIS_PROMPT.format(content=body))

    frontmatter = build_frontmatter(detail)
    note = (
        f"{frontmatter}\n\n# {detail['title']}\n\n"
        f"{ai_analysis}\n\n---\n\n## 📄 原文內容\n\n{body}\n{comments_md}"
    )
    date_str = _fmt_date(detail["publish_at"])
    safe_title = re.sub(r'[\\/:*?"<>|]', "_", detail["title"])
    file_name = f"{date_str}_{safe_title}.md"
    note_path = obsidian.write_note(file_name, note, target_dir=config.SOURCES_DIR)

    mark_synced(detail)
    return {
        "article_id": detail["article_id"],
        "title": detail["title"],
        "type": detail["type"],
        "images": f"{img_ok}/{len(detail['images'])}",
        "comments": len(detail["comments"]),
        "note": str(note_path),
    }


def run(limit_per_salon: int | None = None) -> dict:
    salons = config.VOCUS_SALONS
    if not salons:
        return {"status": "idle", "message": "未設定 VOCUS_SALONS"}

    limit = limit_per_salon if limit_per_salon is not None else config.VOCUS_MAX_PER_RUN
    client = vocus.VocusClient()
    processed, skipped, failed = [], 0, []

    for salon in salons:
        sid, sname = salon["salon_id"], salon["salon_name"]
        logger.info("處理沙龍：%s (%s)", sname, sid)
        count = 0
        for meta in client.iter_all_articles(sid):
            if already_synced(meta["contentId"]):
                skipped += 1
                continue
            if count >= limit:
                logger.info("沙龍 %s 達單次上限 %s，其餘留待下次", sname, limit)
                break
            try:
                result = process_article(client, meta, sname)
                processed.append(result)
                telegram.send_message(
                    config.STOCK_COLLECT_CHAT_ID,
                    f"{sname}的《{result['title']}》已建立完成",
                )
                count += 1
                time.sleep(2)
            except Exception as e:
                logger.exception("處理文章失敗 article_id=%s", meta.get("contentId"))
                failed.append({"article_id": meta.get("contentId"), "error": str(e)})

    result = {
        "status": "ok",
        "processed": len(processed),
        "skipped": skipped,
        "failed": len(failed),
        "details": processed,
        "failures": failed,
    }
    logger.info("vocus_collect 完成：processed=%s skipped=%s failed=%s",
                len(processed), skipped, len(failed))
    return result

"""股票資訊收集分析（原 n8n flow「股票資訊收集分析 - SCHEDULE」vzqX5VmmeDkoovX6 移植）。

流程：輪詢 stocks_raw_data（以訊息 "done" 結批）→ 合併文字與附件、展開 URL →
claude CLI 分析（多圖 / 純文字）→ 組 Obsidian 筆記寫入 00_Inbox + 附件寫 Assets →
Telegram 通知 → 標記 status=2。

與 n8n 版的差異：
- 已處理 rows 改為標記 status=2（原版直接 DELETE），出錯可追溯重跑
- 分析引擎由 Gemini 改為 claude CLI（圖片以本地檔案路徑交給 Claude 讀取）
- 處理中 rows 標記 status=1 鎖定，失敗時保留 status=1 並發 TG 告警，不會無限重試
"""

import logging
import mimetypes
import re
import shutil
import time
import uuid
from datetime import datetime
from pathlib import Path

import config
from services import db, llm, obsidian, telegram, webfetch

logger = logging.getLogger(__name__)

URL_RE = re.compile(r"https?://[^\s]+")

PROMPT_MULTI_IMAGE = """你是一位專業的「知識架構師」與「數位大腦管理專家」。你將接收到多模態資料（文字與圖片檔案）。

你的任務是將這些碎片資訊進行深度解構，轉化為一份邏輯清晰、具備「知識索引」能力的結構化 JSON。

### 🖼️ 圖片檔案
請先逐一讀取以下圖片檔案（使用 Read 工具），再進行分析：
{image_paths}

### 📋 任務指令與輸出格式
請根據輸入內容（含圖片細節），產出以下一個純 JSON 物件：

{{
  "subject": "筆記標題 (簡短有力)",
  "category": "領域分類 (例如：金融投資 / 技術開發 / 項目管理)",
  "main_entities": "主要主體 (※關鍵：請使用 [[雙向連結]] 標註，例如 [[2330]], [[Java]], [[AI Agent]])",
  "key_points": [
    "關鍵要點1 (※關鍵：若提到核心概念請用 [[ ]] 標註)",
    "關鍵要點2 (※請包含圖片中觀察到的核心數據或視覺重點)",
    "..."
  ],
  "content_analysis": "內容深度解析 (※請根據『四色建模法』標註線索：[[Moment-interval]] 標註事件，[[Description]] 標註定義。若有圖片，請描述圖片內容與文字的關聯)",
  "insight_or_action": "洞察與後續行動 (基於圖文內容給出的邏輯建議；若無則填『存檔觀察』)",
  "tags": ["標籤1", "標籤2", "..."],
  "sentiment": "情緒/傾向判讀 (正面 / 負面 / 中立 / 觀察 / 看多 / 看空)"
}}

### 🚨 核心行為準則
1. 【視覺事實驅動】：請詳細解讀圖片。**必須優先辨識圖片左上角或標題處的股票名稱與代碼**。
2. 【索引標準化】：為了銜接正式編譯 (Step 3)，請將所有「公司、技術、專有名詞、特定事件」一律使用 [[雙向連結]] 標記。
3. 【嚴禁幻覺與腦補】：僅限於提供的圖文內容。**嚴禁提及文中與圖中皆未出現的股票名稱**。若無法確認則標註「N/A」。
4. 【文字引導優先】：**若文字中明確提到某代碼（如 2481），請以該文字描述為核心語境去核對圖片內容**。若圖片內容與文字代碼明顯衝突，請在 content_analysis 中客觀指出：「文字標註為 [[2481]] 但圖片顯示為 [[3711]]」，嚴禁自行發揮解釋其作用。
5. 【多模態驗證】：**圖片中的文字（OCR）具備最高證據等級**。請勿將圖片 A 的指標應用到圖片 B 的個股上，除非兩者在同一張圖內。

### ⚠️ 輸出限制
- 你的回覆必須是純 JSON 物件，禁止包含任何 Markdown 區塊標記 (如 ```json)，禁止任何 JSON 以外的說明文字。
- 語系：繁體中文。

----------------
待處理內容：
{context_text}"""

PROMPT_TEXT_ONLY = """你是一位專業的「知識架構師」與「數位大腦管理專家」。你將接收到一段由多則訊息合併而成的長文。

你的任務是將這些資訊深度解構，轉化為一份邏輯清晰、便於未來檢索的結構化 JSON。

### 📋 任務指令與輸出格式
請根據輸入內容，產出以下一個純 JSON 物件：

{{
  "subject": "筆記標題 (簡短有力)",
  "category": "領域分類 (例如：金融投資 / 技術開發 / 項目管理 / 生活紀錄)",
  "main_entities": "主要主體 (※關鍵：請使用 [[雙向連結]] 標註，例如 [[2330]], [[Java]], [[AI Agent]])",
  "key_points": [
    "關鍵要點1 (※關鍵：若提到核心概念請用 [[ ]] 標註)",
    "關鍵要點2",
    "..."
  ],
  "content_analysis": "內容深度解析 (※請根據『四色建模法』標註線索：[[Moment-interval]] 標註事件，[[Description]] 標註定義)",
  "insight_or_action": "洞察與後續行動 (基於內容給出的邏輯建議；若無則填『存檔觀察』)",
  "tags": ["標籤1", "標籤2", "..."],
  "sentiment": "情緒/傾向判讀 (正面 / 負面 / 中立 / 觀察 / 看多 / 看空)"
}}

### 🚨 核心行為準則
1. 【100% 事實導向】：嚴禁捏造資訊。若無明確資訊請填「未提及」。
2. 【索引標準化】：為了銜接未來的正式編譯 (Step 3)，請將所有「公司、技術、專有名詞、特定事件」一律封裝在 [[ ]] 中。
3. 【語系要求】：一律使用繁體中文。

### ⚠️ 輸出限制
- 你的回覆必須是純 JSON 物件，禁止包含任何 Markdown 區塊標記 (如 ```json)，禁止任何 JSON 以外的說明文字。

----------------
待處理內容：
{context_text}"""


def fetch_batch(chat_id: str) -> list[dict]:
    """撈出這個聊天室裡，到第一筆 'done' 為止的未處理訊息（含 done 本身）。"""
    return db.fetch_all(
        """
        SELECT * FROM stocks_raw_data
        WHERE chat_id = %s
          AND status = 0
          AND id <= (
              SELECT MIN(id) FROM stocks_raw_data
              WHERE chat_id = %s AND status = 0 AND content_text = 'done'
          )
        ORDER BY id ASC
        """,
        (chat_id, chat_id),
    )


def set_status(ids: list[int], status: int) -> None:
    if not ids:
        return
    placeholders = ",".join(["%s"] * len(ids))
    db.execute(
        f"UPDATE stocks_raw_data SET status = %s, updated_at = NOW() WHERE id IN ({placeholders})",
        [status, *ids],
    )


def merge_rows(rows: list[dict]) -> dict:
    """合併批次訊息：文字串接 + URL 展開 + 附件轉 Obsidian 連結語法。"""
    full_text = ""
    files: list[dict] = []
    seen_file_ids: set[str] = set()
    img_counter = 0
    doc_counter = 0

    def append_line(text: str) -> None:
        nonlocal full_text
        if full_text:
            full_text += "\n" if text.startswith(("![[", "📎")) else "\n\n"
        full_text += text

    for row in rows:
        content = row.get("content_text")
        if content and content.strip().lower() != "done":
            processed = content
            for url in dict.fromkeys(URL_RE.findall(content)):
                processed += webfetch.build_url_block(url)
            append_line(processed)

        if row.get("message_type") in ("IMAGE", "DOCUMENT") and row.get("telegram_file_id"):
            file_id = row["telegram_file_id"]
            if file_id in seen_file_ids:
                continue
            seen_file_ids.add(file_id)
            actual_name = row.get("file_name")
            if row["message_type"] == "IMAGE":
                name = actual_name or f"image_{img_counter}.jpg"
                img_counter += 1
                label = f"![[{name}]]"
            else:
                name = actual_name or f"document_{doc_counter}"
                doc_counter += 1
                label = f"📎 [[{name}]]"
            files.append({"file_id": file_id, "file_name": name, "type": row["message_type"]})
            append_line(label)

    return {"full_text": full_text, "files": files}


def download_and_save_files(files: list[dict], work_dir: Path) -> list[dict]:
    """下載附件：全部寫入 Obsidian Assets；圖片另存工作目錄供 claude 讀取。"""
    downloads = []
    for i, f in enumerate(files):
        if i > 0:
            time.sleep(1)
        data = telegram.download_file(f["file_id"])
        if data is None:
            logger.error("附件下載失敗，略過：%s", f["file_name"])
            continue
        asset_path = obsidian.write_attachment(f["file_name"], data)
        mime = mimetypes.guess_type(f["file_name"])[0] or ""
        is_image = f["type"] == "IMAGE" or mime.startswith("image/")
        local_path = None
        if is_image:
            local_path = work_dir / obsidian.sanitize_filename(f["file_name"])
            local_path.write_bytes(data)
        downloads.append(
            {"file_name": f["file_name"], "asset_path": asset_path, "image_path": local_path}
        )
    return downloads


def analyze(full_text: str, image_paths: list[Path], work_dir: Path) -> dict:
    if image_paths:
        prompt = PROMPT_MULTI_IMAGE.format(
            image_paths="\n".join(f"- {p}" for p in image_paths),
            context_text=full_text,
        )
    else:
        prompt = PROMPT_TEXT_ONLY.format(context_text=full_text)
    raw = llm.run_claude(prompt, cwd=str(work_dir))
    try:
        return llm.parse_json_output(raw)
    except llm.ClaudeError:
        # 與 n8n 版行為一致：解析失敗時把原始回覆放進 content_analysis
        logger.warning("AI 回覆無法解析為 JSON，改以原文存入 content_analysis")
        return {
            "subject": "新筆記",
            "category": "未分類",
            "main_entities": "N/A",
            "key_points": [],
            "content_analysis": raw,
            "insight_or_action": "存檔觀察",
            "tags": [],
            "sentiment": "觀察",
        }


def build_markdown(ai: dict, original_context: str, chat_id: str) -> tuple[str, str]:
    """組出 Obsidian 筆記內容與檔名（移植自 n8n「Code in JavaScript」節點）。"""
    subject = str(ai.get("subject") or "Note")
    date_str = datetime.now().strftime("%Y-%m-%d")
    safe_subject = re.sub(r'[\\/:*?"<>|]', "_", subject)[:30]
    md_file_name = f"{date_str}_{safe_subject}.md"

    entities = ai.get("main_entities") or "N/A"
    if isinstance(entities, list):
        entities = ", ".join(str(e) for e in entities)

    key_points = ai.get("key_points") or []
    if not isinstance(key_points, list):
        key_points = [str(key_points)]

    tags = ai.get("tags") or []
    if not isinstance(tags, list):
        tags = [str(tags)]
    clean_tags = [str(t).lstrip("#") for t in tags]

    content_analysis = str(ai.get("content_analysis") or "無")
    insight = str(ai.get("insight_or_action") or "無")

    lines = [
        "---",
        f'title: "{subject}"',
        f'category: "{ai.get("category") or "未分類"}"',
        f'sentiment: "{ai.get("sentiment") or "觀察"}"',
        f"tags: [{', '.join(clean_tags)}]",
        "---",
        "",
        f"# {subject}",
        "",
        "> [!abstract] 核心摘要與 Wiki 索引標籤",
        f"> 1. **💡 主要主體 (Entities)**: {entities}",
        "> 2. **🎯 關鍵要點**:",
        *[f">    - {p}" for p in key_points],
        ">",
        "> 3. **🧠 內容解析 (Wiki Nodes)**:",
        "> " + content_analysis.replace("\n", "\n> "),
        ">",
        "> 4. **🚀 洞察與行動**:",
        "> " + insight.replace("\n", "\n> "),
        "",
        "---",
        "## 📝 原始內容",
        "> [!quote] 展開原始訊息",
        "> " + original_context.replace("\n", "\n> "),
        "",
        f"*ID: {chat_id}*",
    ]
    return md_file_name, "\n".join(lines)


def run(chat_id: str | None = None) -> dict:
    chat_id = chat_id or config.STOCK_COLLECT_CHAT_ID
    rows = fetch_batch(chat_id)
    if not rows:
        return {"status": "idle", "message": "無待處理批次（未見 done 信號）"}

    ids = [r["id"] for r in rows]
    logger.info("開始處理批次 chat_id=%s rows=%s ids=%s", chat_id, len(rows), ids)
    set_status(ids, 1)

    work_dir = config.TMP_DIR / f"batch_{uuid.uuid4().hex[:8]}"
    work_dir.mkdir(parents=True, exist_ok=True)
    try:
        merged = merge_rows(rows)
        downloads = download_and_save_files(merged["files"], work_dir)
        image_paths = [d["image_path"] for d in downloads if d["image_path"]]

        ai = analyze(merged["full_text"], image_paths, work_dir)
        md_name, md_content = build_markdown(ai, merged["full_text"], chat_id)
        note_path = obsidian.write_note(md_name, md_content)

        set_status(ids, 2)
        telegram.send_message(chat_id, f"已產生分析檔案\n{md_name}")
        result = {
            "status": "ok",
            "rows": len(rows),
            "note": str(note_path),
            "attachments": [str(d["asset_path"]) for d in downloads],
            "subject": ai.get("subject"),
        }
        logger.info("批次完成：%s", result)
        return result
    except Exception as e:
        logger.exception("批次處理失敗 ids=%s", ids)
        telegram.send_message(
            chat_id,
            f"❌ 股票資訊收集分析失敗：{e}\n批次 rows 已標記 status=1，修復後請將其改回 0 重跑。",
        )
        raise
    finally:
        shutil.rmtree(work_dir, ignore_errors=True)

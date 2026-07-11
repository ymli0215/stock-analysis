import json
import logging
import os
import re
import subprocess

import config

logger = logging.getLogger(__name__)


class ClaudeError(RuntimeError):
    pass


def run_claude(prompt: str, cwd: str | None = None, timeout: int | None = None) -> str:
    """以 claude CLI 執行單次分析任務，回傳純文字結果。

    僅允許 Read 工具（讀取圖片用）：分析的輸入來自 Telegram 與網頁擷取內容，
    屬不可信輸入，不給 claude 任何寫檔/執行指令的能力，寫檔與 DB 操作
    一律由本程式的確定性程式碼完成。
    """
    timeout = timeout or config.CLAUDE_TIMEOUT_SECONDS
    try:
        result = subprocess.run(
            [
                config.CLAUDE_BIN,
                "-p",
                prompt,
                "--output-format",
                "json",
                "--allowedTools",
                "Read",
                "--disallowedTools",
                "Bash,Write,Edit,WebFetch,WebSearch,NotebookEdit,Agent,Task",
            ],
            cwd=cwd,
            capture_output=True,
            text=True,
            timeout=timeout,
            env={**os.environ, "CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS": "0"},
        )
    except subprocess.TimeoutExpired as e:
        raise ClaudeError(f"claude CLI 執行逾時（{timeout}s）") from e
    except OSError as e:
        raise ClaudeError(f"無法啟動 claude CLI：{e}") from e

    if result.returncode != 0:
        raise ClaudeError(f"claude CLI 結束碼 {result.returncode}：{result.stderr[:500]}")

    try:
        parsed = json.loads(result.stdout)
        if parsed.get("is_error"):
            raise ClaudeError(f"claude 回報錯誤：{str(parsed.get('result'))[:500]}")
        return parsed.get("result", result.stdout)
    except json.JSONDecodeError:
        logger.warning("claude 輸出非 JSON，回退使用原始 stdout")
        return result.stdout


def parse_json_output(text: str) -> dict:
    """從 LLM 回覆中解析出 JSON 物件，容忍 ```json 圍欄與前後雜訊。"""
    cleaned = text.replace("```json", "").replace("```", "").strip()
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        m = re.search(r"\{.*\}", cleaned, re.DOTALL)
        if m:
            try:
                return json.loads(m.group(0))
            except json.JSONDecodeError:
                pass
    raise ClaudeError(f"無法從 LLM 回覆解析 JSON：{text[:300]}")

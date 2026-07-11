import os
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent
load_dotenv(BASE_DIR / ".env")


def env(key: str, default: str | None = None) -> str | None:
    return os.environ.get(key, default)


def env_bool(key: str, default: bool = False) -> bool:
    v = os.environ.get(key)
    if v is None:
        return default
    return v.strip().lower() in ("1", "true", "yes", "on")


# --- Database (mysql84 / stockapp) ---
DB_HOST = env("DB_HOST", "mysql84")
DB_PORT = int(env("DB_PORT", "3306"))
DB_NAME = env("DB_NAME", "stockapp")
DB_USER = env("DB_USER", "root")
DB_PASSWORD = env("DB_PASSWORD", "")

# --- Telegram（bot A：webhook /stockinfo 入庫 stocks_raw_data 的那隻，file_id 綁定此 bot）---
TELEGRAM_BOT_TOKEN = env("TELEGRAM_BOT_TOKEN", "")
# 群組通知用 bot（bot B 在 bignoodle stock group 群組內；bot A 不在，未設定時退回 bot A）
TELEGRAM_NOTIFY_BOT_TOKEN = env("TELEGRAM_NOTIFY_BOT_TOKEN", "") or TELEGRAM_BOT_TOKEN
TELEGRAM_API_BASE = "https://api.telegram.org"
TELEGRAM_MESSAGE_LIMIT = 4000
TELEGRAM_MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024  # Bot API getFile 上限

# --- stock_collect job ---
STOCK_COLLECT_ENABLED = env_bool("STOCK_COLLECT_ENABLED", False)
STOCK_COLLECT_CHAT_ID = env("STOCK_COLLECT_CHAT_ID", "2130788733")
STOCK_COLLECT_INTERVAL_SECONDS = int(env("STOCK_COLLECT_INTERVAL_SECONDS", "120"))

# --- StockServer 維運排程 ---
SYNC_STOCK_DATA_ENABLED = env_bool("SYNC_STOCK_DATA_ENABLED", False)
IMPORT_WARRANT_ENABLED = env_bool("IMPORT_WARRANT_ENABLED", False)
STOCKSERVER_BASE = env("STOCKSERVER_BASE", "http://stock:8080/StockServer")
NOTIFY_GROUP_CHAT_ID = env("NOTIFY_GROUP_CHAT_ID", "-4921060460")

# --- Obsidian 輸出 ---
OBSIDIAN_ROOT = Path(env("OBSIDIAN_ROOT", "/obsidian"))
NOTE_DIR = OBSIDIAN_ROOT / "00_Inbox"
ASSET_DIR = OBSIDIAN_ROOT / "Assets"
SOURCES_DIR = OBSIDIAN_ROOT / "01_Sources"

# --- Vocus 沙龍收集（取代 Get Vocus flow）---
VOCUS_EMAIL = env("VOCUS_EMAIL", "")
VOCUS_PASSWORD = env("VOCUS_PASSWORD", "")
VOCUS_COLLECT_ENABLED = env_bool("VOCUS_COLLECT_ENABLED", False)
# 每次執行每個沙龍最多處理幾篇（避免首次全量 1000+ 篇一次跑爆；分批消化）
VOCUS_MAX_PER_RUN = int(env("VOCUS_MAX_PER_RUN", "5"))
# 排程：預設每日 06:00 與 18:00（比照原 n8n flow）
VOCUS_CRON_HOURS = env("VOCUS_CRON_HOURS", "6,18")
# 沙龍清單：格式 "salonId:名稱,salonId:名稱"（新增沙龍加一筆即可）
_salons_raw = env(
    "VOCUS_SALONS",
    "6453433ffd897800018c4efa:邏輯投資,65364e53fd89780001c37e92:摩股史塔克",
)
VOCUS_SALONS = [
    {"salon_id": s.split(":", 1)[0].strip(), "salon_name": s.split(":", 1)[1].strip()}
    for s in _salons_raw.split(",")
    if ":" in s
]

# --- LLM（claude CLI）---
CLAUDE_BIN = env("CLAUDE_BIN", "claude")
CLAUDE_TIMEOUT_SECONDS = int(env("CLAUDE_TIMEOUT_SECONDS", "900"))

# --- 其他 ---
LOG_DIR = BASE_DIR / "logs"
TMP_DIR = BASE_DIR / "tmp"
JINA_MAX_LENGTH = 15000

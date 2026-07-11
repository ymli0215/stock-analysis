import logging
import re
from pathlib import Path

import config

logger = logging.getLogger(__name__)


def sanitize_filename(name: str) -> str:
    name = re.sub(r'[\/\\:*?"<>|]', "_", str(name or "untitled"))
    name = re.sub(r"\s+", " ", name).strip()
    return name[:180]


def write_note(file_name: str, content: str, target_dir: Path | None = None) -> Path:
    target = target_dir or config.NOTE_DIR
    target.mkdir(parents=True, exist_ok=True)
    path = target / sanitize_filename(file_name)
    path.write_text(content, encoding="utf-8")
    logger.info("筆記已寫入 %s (%d bytes)", path, len(content.encode("utf-8")))
    return path


def write_attachment(file_name: str, data: bytes) -> Path:
    config.ASSET_DIR.mkdir(parents=True, exist_ok=True)
    path = config.ASSET_DIR / sanitize_filename(file_name)
    path.write_bytes(data)
    logger.info("附件已寫入 %s (%d bytes)", path, len(data))
    return path

"""Shared pymysql connection factory. Credentials loaded from python/.env."""

import os
from pathlib import Path

import pymysql
from dotenv import load_dotenv

load_dotenv(Path(__file__).parent.parent / ".env")


def get_conn() -> pymysql.connections.Connection:
    """Return a new pymysql connection using env-based credentials."""
    return pymysql.connect(
        host=os.environ["DB_HOST"],
        port=int(os.environ.get("DB_PORT", 3306)),
        user=os.environ["DB_USER"],
        password=os.environ["DB_PASSWORD"],
        database=os.environ["DB_NAME"],
        charset="utf8mb4",
        autocommit=False,
    )

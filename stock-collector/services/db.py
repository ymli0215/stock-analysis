import logging
from contextlib import contextmanager

import pymysql

import config

logger = logging.getLogger(__name__)


@contextmanager
def get_conn():
    conn = pymysql.connect(
        host=config.DB_HOST,
        port=config.DB_PORT,
        user=config.DB_USER,
        password=config.DB_PASSWORD,
        database=config.DB_NAME,
        charset="utf8mb4",
        autocommit=True,
        cursorclass=pymysql.cursors.DictCursor,
        connect_timeout=10,
    )
    try:
        yield conn
    finally:
        conn.close()


def fetch_all(sql: str, params=None) -> list[dict]:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            return list(cur.fetchall())


def execute(sql: str, params=None) -> int:
    with get_conn() as conn:
        with conn.cursor() as cur:
            affected = cur.execute(sql, params)
            return affected

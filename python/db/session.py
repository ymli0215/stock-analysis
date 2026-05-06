"""Database session factory and context-manager helpers."""

import os
from contextlib import contextmanager
from typing import Generator

from dotenv import load_dotenv
from loguru import logger
from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session, sessionmaker

from db.models import Base

load_dotenv()

_DB_HOST = os.getenv("DB_HOST", "mysql57")
_DB_PORT = os.getenv("DB_PORT", "3306")
_DB_NAME = os.getenv("DB_NAME", "stock_analysis")
_DB_USER = os.getenv("DB_USER", "")
_DB_PASSWORD = os.getenv("DB_PASSWORD", "")

DATABASE_URL = (
    f"mysql+pymysql://{_DB_USER}:{_DB_PASSWORD}"
    f"@{_DB_HOST}:{_DB_PORT}/{_DB_NAME}?charset=utf8mb4"
)

engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,
    pool_recycle=3600,
    echo=False,
)

SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False)


def init_db() -> None:
    """Create all tables if they do not already exist.

    Called once at application startup.
    """
    try:
        Base.metadata.create_all(bind=engine)
        logger.info("Database tables initialised.")
    except Exception as exc:
        logger.error(f"Failed to initialise database: {exc}")
        raise


@contextmanager
def get_session() -> Generator[Session, None, None]:
    """Yield a SQLAlchemy Session and handle commit / rollback automatically.

    Usage::

        with get_session() as session:
            session.add(record)
    """
    session: Session = SessionLocal()
    try:
        yield session
        session.commit()
    except Exception as exc:
        session.rollback()
        logger.error(f"Session rollback due to: {exc}")
        raise
    finally:
        session.close()


def check_connection() -> bool:
    """Return True if the database is reachable, False otherwise."""
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        return True
    except Exception as exc:
        logger.error(f"Database connection check failed: {exc}")
        return False

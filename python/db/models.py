"""SQLAlchemy ORM models for stock analysis data."""

from datetime import datetime

from sqlalchemy import (
    BigInteger,
    Column,
    Date,
    DateTime,
    Float,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    pass


class StockPrice(Base):
    """Daily OHLCV price data for any market symbol."""

    __tablename__ = "stock_prices"

    id = Column(Integer, primary_key=True, autoincrement=True)
    symbol = Column(String(20), nullable=False, index=True)
    date = Column(Date, nullable=False)
    open = Column(Float)
    high = Column(Float)
    low = Column(Float)
    close = Column(Float)
    volume = Column(BigInteger)
    adj_close = Column(Float)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)

    __table_args__ = (
        UniqueConstraint("symbol", "date", name="uq_stock_prices_symbol_date"),
        Index("ix_stock_prices_symbol_date", "symbol", "date"),
    )


class StockInfo(Base):
    """Basic company/instrument information."""

    __tablename__ = "stock_info"

    id = Column(Integer, primary_key=True, autoincrement=True)
    symbol = Column(String(20), nullable=False, unique=True, index=True)
    name = Column(String(200))
    market = Column(String(10))        # TW / US / JP
    sector = Column(String(100))
    industry = Column(String(100))
    currency = Column(String(10))
    exchange = Column(String(50))
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)


class Fundamental(Base):
    """Quarterly / annual fundamental data per symbol."""

    __tablename__ = "fundamentals"

    id = Column(Integer, primary_key=True, autoincrement=True)
    symbol = Column(String(20), nullable=False, index=True)
    period = Column(Date, nullable=False)            # report period end date
    period_type = Column(String(10), nullable=False) # 'Q' or 'A'
    revenue = Column(Float)
    net_income = Column(Float)
    eps = Column(Float)
    pe_ratio = Column(Float)
    pb_ratio = Column(Float)
    dividend_yield = Column(Float)
    roe = Column(Float)
    debt_to_equity = Column(Float)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)

    __table_args__ = (
        UniqueConstraint("symbol", "period", "period_type", name="uq_fundamentals_symbol_period"),
    )


class TechnicalIndicator(Base):
    """Pre-computed technical indicators stored per symbol/date."""

    __tablename__ = "technical_indicators"

    id = Column(Integer, primary_key=True, autoincrement=True)
    symbol = Column(String(20), nullable=False, index=True)
    date = Column(Date, nullable=False)
    ma5 = Column(Float)
    ma10 = Column(Float)
    ma20 = Column(Float)
    ma60 = Column(Float)
    rsi14 = Column(Float)
    macd = Column(Float)
    macd_signal = Column(Float)
    macd_hist = Column(Float)
    bb_upper = Column(Float)
    bb_middle = Column(Float)
    bb_lower = Column(Float)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)

    __table_args__ = (
        UniqueConstraint("symbol", "date", name="uq_technical_indicators_symbol_date"),
        Index("ix_technical_indicators_symbol_date", "symbol", "date"),
    )


class CollectorLog(Base):
    """Audit log for data collection runs."""

    __tablename__ = "collector_logs"

    id = Column(Integer, primary_key=True, autoincrement=True)
    symbol = Column(String(20), nullable=False, index=True)
    collector_type = Column(String(20), nullable=False)  # 'price' | 'fundamental'
    status = Column(String(10), nullable=False)          # 'ok' | 'error'
    message = Column(Text)
    rows_upserted = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)

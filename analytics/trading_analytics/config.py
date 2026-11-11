"""Connection and domain settings, all taken from environment variables.

The defaults match docker-compose.yml and application.yml in the repository root, so a developer who
started PostgreSQL with docker compose needs no configuration.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from decimal import Decimal


@dataclass(frozen=True)
class DatabaseSettings:
    host: str
    port: int
    name: str
    user: str
    password: str

    @classmethod
    def from_env(cls, database_name: str | None = None) -> DatabaseSettings:
        return cls(
            host=os.environ.get("TRADING_DB_HOST", "localhost"),
            port=int(os.environ.get("TRADING_DB_PORT", "5432")),
            name=database_name or os.environ.get("TRADING_DB_NAME", "trading_platform"),
            user=os.environ.get("TRADING_DB_USER", "trading_user"),
            password=os.environ.get("TRADING_DB_PASSWORD", "trading_password"),
        )

    def url(self) -> str:
        return f"postgresql+psycopg://{self.user}:{self.password}@{self.host}:{self.port}/{self.name}"


@dataclass(frozen=True)
class DomainSettings:
    """Values the Java application configures but does not store in the database."""

    # app.risk.max-order-notional in application.yml
    max_order_notional: Decimal
    # app.market-maker.shares-per-instrument: shares granted to each SYSTEM account when it is created.
    # There is no share ledger, so the grant cannot be read back from the database.
    market_maker_shares_per_instrument: int

    @classmethod
    def from_env(cls) -> DomainSettings:
        return cls(
            max_order_notional=Decimal(os.environ.get("TRADING_MAX_ORDER_NOTIONAL", "100000")),
            market_maker_shares_per_instrument=int(
                os.environ.get("TRADING_MARKET_MAKER_SHARES_PER_INSTRUMENT", "100000")
            ),
        )

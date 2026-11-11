"""Test database: a real PostgreSQL with the schema created from the repository's Flyway migrations.

The tests need a PostgreSQL server (docker compose up -d postgres, or the CI service container) reachable
with the TRADING_DB_* settings. They create their own database, TRADING_TEST_DB_NAME, so the application's
database is never touched.
"""

from __future__ import annotations

import os
import re
from collections.abc import Iterator
from pathlib import Path

import pytest
from sqlalchemy import Engine, create_engine, text

from tests.scenario import Scenario, build_scenario
from trading_analytics.config import DatabaseSettings, DomainSettings

REPO_ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = REPO_ROOT / "src" / "main" / "resources" / "db" / "migration"
TEST_DB_NAME = os.environ.get("TRADING_TEST_DB_NAME", "trading_platform_analytics_test")

# Tables in dependency order, for truncation. instruments keeps its Flyway seed rows.
DATA_TABLES = (
    "trades",
    "cash_ledger_entries",
    "orders",
    "positions",
    "cash_balances",
    "accounts",
    "app_users",
)


def migration_files() -> list[Path]:
    files = sorted(MIGRATIONS.glob("V*__*.sql"), key=lambda p: int(re.match(r"V(\d+)__", p.name).group(1)))
    assert files, f"No Flyway migrations found in {MIGRATIONS}"
    return files


@pytest.fixture(scope="session")
def test_settings() -> DatabaseSettings:
    return DatabaseSettings.from_env(database_name=TEST_DB_NAME)


@pytest.fixture(scope="session")
def admin_engine(test_settings: DatabaseSettings) -> Iterator[Engine]:
    engine = create_engine(DatabaseSettings.from_env().url(), isolation_level="AUTOCOMMIT")
    with engine.connect() as connection:
        exists = connection.execute(
            text("SELECT 1 FROM pg_database WHERE datname = :name"), {"name": test_settings.name}
        ).scalar()
        if not exists:
            connection.execute(text(f'CREATE DATABASE "{test_settings.name}"'))
    yield engine
    engine.dispose()


@pytest.fixture(scope="session")
def write_engine(admin_engine: Engine, test_settings: DatabaseSettings) -> Iterator[Engine]:
    """Engine for the test's own writes: schema creation, fixture data and fault injection."""
    engine = create_engine(test_settings.url(), isolation_level="AUTOCOMMIT")
    raw = engine.raw_connection()
    try:
        cursor = raw.cursor()
        cursor.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;")
        for migration in migration_files():
            # No parameters, so psycopg uses the simple query protocol and runs every statement in the file.
            cursor.execute(migration.read_text(encoding="utf-8"))
        raw.commit()
    finally:
        raw.close()
    yield engine
    engine.dispose()


@pytest.fixture
def domain_settings() -> DomainSettings:
    return DomainSettings(
        max_order_notional=100_000, market_maker_shares_per_instrument=Scenario.MARKET_MAKER_SHARES
    )


@pytest.fixture
def scenario(write_engine: Engine) -> Scenario:
    """A consistent trading history, rebuilt from scratch for every test."""
    with write_engine.connect() as connection:
        connection.execute(text(f"TRUNCATE {', '.join(DATA_TABLES)} RESTART IDENTITY CASCADE"))
        connection.execute(text("UPDATE instruments SET last_trade_price = NULL"))
        return build_scenario(connection)

"""Database access. Every connection handed out here runs read-only transactions."""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from importlib import resources

import pandas as pd
from sqlalchemy import Connection, Engine, create_engine, text

from trading_analytics.config import DatabaseSettings


def make_engine(settings: DatabaseSettings) -> Engine:
    return create_engine(settings.url())


@contextmanager
def read_only_connection(engine: Engine) -> Iterator[Connection]:
    """A connection whose transactions PostgreSQL enforces as READ ONLY.

    The analytics module must never change business tables. Rather than trusting the code, the
    guarantee is pushed to the database: any INSERT, UPDATE or DELETE on this connection fails.
    """
    with engine.connect().execution_options(postgresql_readonly=True) as connection:
        yield connection
        connection.rollback()


def load_sql(name: str) -> str:
    return resources.files("trading_analytics.sql").joinpath(f"{name}.sql").read_text(encoding="utf-8")


def read_frame(connection: Connection, query_name: str) -> pd.DataFrame:
    # coerce_float=False keeps NUMERIC columns as Decimal: money must be compared exactly, not as float.
    return pd.read_sql_query(text(load_sql(query_name)), connection, coerce_float=False)

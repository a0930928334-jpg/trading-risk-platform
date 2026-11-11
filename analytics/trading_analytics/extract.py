"""Loads the tables the checks and metrics work on, in one read-only snapshot."""

from __future__ import annotations

from dataclasses import dataclass, fields

import pandas as pd
from sqlalchemy import Engine

from trading_analytics.db import read_frame, read_only_connection

OPEN_STATUSES = ("NEW", "PARTIALLY_FILLED")
SYSTEM_USER_STATUS = "SYSTEM"


@dataclass
class Dataset:
    instruments: pd.DataFrame
    accounts: pd.DataFrame
    cash_balances: pd.DataFrame
    cash_ledger_entries: pd.DataFrame
    positions: pd.DataFrame
    orders: pd.DataFrame
    trades: pd.DataFrame

    @property
    def system_account_ids(self) -> set[int]:
        system = self.accounts[self.accounts["user_status"] == SYSTEM_USER_STATUS]
        return {int(account_id) for account_id in system["account_id"]}

    @property
    def open_orders(self) -> pd.DataFrame:
        return self.orders[self.orders["status"].isin(OPEN_STATUSES)]

    def row_counts(self) -> dict[str, int]:
        return {field.name: len(getattr(self, field.name)) for field in fields(self)}


def extract(engine: Engine) -> Dataset:
    """Reads every table in one REPEATABLE READ transaction so the checks see a consistent snapshot."""
    with read_only_connection(engine) as connection:
        snapshot = connection.execution_options(isolation_level="REPEATABLE READ")
        frames = {field.name: read_frame(snapshot, field.name) for field in fields(Dataset)}
    return Dataset(**frames)

"""The analytics connection cannot write, and extraction reads every table."""

from __future__ import annotations

from decimal import Decimal

import pytest
from sqlalchemy import text
from sqlalchemy.exc import DBAPIError

from tests.test_checks import make_engine_for
from trading_analytics.db import read_only_connection
from trading_analytics.extract import extract


def test_writes_through_the_analytics_connection_are_refused(write_engine, scenario):
    engine = make_engine_for(write_engine)
    with read_only_connection(engine) as connection, pytest.raises(DBAPIError) as error:
        connection.execute(text("UPDATE cash_balances SET balance = 0"))
    assert "read-only" in str(error.value).lower()
    engine.dispose()


def test_extract_reads_every_table(write_engine, scenario):
    engine = make_engine_for(write_engine)
    dataset = extract(engine)
    engine.dispose()
    counts = dataset.row_counts()
    assert counts["instruments"] == 5
    assert counts["accounts"] == 3
    assert counts["cash_balances"] == 3
    assert counts["orders"] == 8
    assert counts["trades"] == 2
    assert counts["positions"] == 6
    assert dataset.system_account_ids == {scenario.market_maker}
    # NUMERIC columns must arrive as Decimal; a float would make 400.10 x 10 unequal to 4001.0000.
    assert all(isinstance(price, Decimal) for price in dataset.orders["price"])
    assert all(isinstance(amount, Decimal) for amount in dataset.cash_ledger_entries["amount"])
    assert set(dataset.open_orders["id"]) == {
        scenario.mm_aapl_bid,
        scenario.mm_tsla_ask,
        scenario.mm_tsla_bid,
        scenario.alice_aapl_buy,
    }

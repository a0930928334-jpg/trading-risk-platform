"""Metrics computed on the scenario, checked against values worked out by hand."""

from __future__ import annotations

import pytest

from tests.test_checks import make_engine_for
from trading_analytics import metrics
from trading_analytics.extract import extract


@pytest.fixture
def computed(write_engine, scenario):
    engine = make_engine_for(write_engine)
    dataset = extract(engine)
    engine.dispose()
    return metrics.compute(dataset), scenario


def test_overview(computed):
    result, _ = computed
    overview = result.overview
    # 8 orders: 4 market maker quotes, Alice's buy and sell, Bob's cancelled bid and rejected bid.
    assert overview["orders"] == 8
    assert overview["rejected_orders"] == 1
    assert overview["reject_rate"] == pytest.approx(1 / 8)
    # Filled: the market maker's AAPL ask and Alice's sell. Accepted: 7.
    assert overview["fill_rate"] == pytest.approx(2 / 7)
    assert overview["user_cancel_rate"] == pytest.approx(1 / 7)
    assert overview["open_orders"] == 4
    assert overview["trades"] == 2
    assert overview["traded_shares"] == 130
    assert overview["traded_notional"] == pytest.approx(100 * 191 + 30 * 189)


def test_by_reject_code(computed):
    result, _ = computed
    table = result.by_reject_code
    assert list(table["reject_code"]) == ["PRICE_OUT_OF_BAND"]
    assert table.loc[0, "orders"] == 1
    assert table.loc[0, "rate_of_all_orders"] == pytest.approx(1 / 8)


def test_by_instrument(computed):
    result, _ = computed
    table = result.by_instrument.set_index("symbol")
    assert table.loc["AAPL", "orders"] == 4
    assert table.loc["AAPL", "trades"] == 2
    assert table.loc["AAPL", "traded_shares"] == 130
    assert table.loc["NVDA", "reject_rate"] == pytest.approx(1.0)
    assert table.loc["MSFT", "user_cancel_rate"] == pytest.approx(1.0)


def test_time_to_first_fill(computed):
    result, _ = computed
    table = result.time_to_first_fill.set_index("role")
    # Takers: Alice's buy (placed 5 s before its fill) and Alice's sell (3 s).
    assert table.loc["taker", "orders"] == 2
    assert table.loc["taker", "max_seconds"] == pytest.approx(5.0)
    # Makers: the market maker's AAPL ask and bid, which rested for longer.
    assert table.loc["maker", "orders"] == 2
    assert table.loc["maker", "p50_seconds"] > table.loc["taker", "max_seconds"]


def test_by_account(computed):
    result, scenario = computed
    table = result.by_account.set_index("account_id")
    assert table.loc[scenario.market_maker, "system_account"]
    assert table.loc[scenario.alice, "orders"] == 2
    assert table.loc[scenario.alice, "trades"] == 2
    assert table.loc[scenario.alice, "orders_per_trade"] == pytest.approx(1.0)
    assert table.loc[scenario.bob, "reject_rate"] == pytest.approx(0.5)
    assert table.loc[scenario.bob, "user_cancel_rate"] == pytest.approx(1.0)
    assert table.loc[scenario.bob, "trades"] == 0


def test_over_time_buckets_sum_to_totals(computed):
    result, _ = computed
    table = result.over_time
    assert table["orders"].sum() == 8
    assert table["trades"].sum() == 2
    assert table["rejected"].sum() == 1
    assert table["user_cancelled"].sum() == 1

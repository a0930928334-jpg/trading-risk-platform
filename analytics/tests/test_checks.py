"""The seven checks pass on consistent data, and each injected fault is caught by exactly the right check(s)
with the right identifiers."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass

import pytest
from sqlalchemy import Engine, text

from tests.scenario import Scenario
from trading_analytics import checks
from trading_analytics.config import DomainSettings
from trading_analytics.db import make_engine
from trading_analytics.extract import extract


def run_checks(write_engine: Engine, domain_settings: DomainSettings) -> dict[str, checks.CheckResult]:
    engine = make_engine_for(write_engine)
    results = checks.run_all(extract(engine), domain_settings)
    engine.dispose()
    return {result.name: result for result in results}


def make_engine_for(write_engine: Engine) -> Engine:
    from trading_analytics.config import DatabaseSettings

    url = write_engine.url
    return make_engine(DatabaseSettings(url.host, url.port, url.database, url.username, url.password or ""))


def test_every_check_passes_on_a_consistent_history(write_engine, scenario, domain_settings):
    results = run_checks(write_engine, domain_settings)
    assert set(results) == set(checks.CHECK_NAMES)
    failed = {name: [f.message for f in r.findings] for name, r in results.items() if not r.passed}
    assert failed == {}
    assert all(result.rows_checked > 0 for result in results.values())


@dataclass
class Fault:
    name: str
    sql: Callable[[Scenario], str]
    fails: set[str]
    # (check name, finding) -> whether the finding points at the corrupted row
    points_at: Callable[[Scenario, checks.Finding], bool]


def _ledger_id(write_engine: Engine, account_id: int, entry_type: str) -> int:
    with write_engine.connect() as connection:
        return int(
            connection.execute(
                text(
                    "SELECT id FROM cash_ledger_entries WHERE account_id = :account AND entry_type = :type "
                    "ORDER BY id LIMIT 1"
                ),
                {"account": account_id, "type": entry_type},
            ).scalar_one()
        )


FAULTS = [
    Fault(
        "ledger snapshot off by one cent",
        lambda s: (
            "UPDATE cash_ledger_entries SET balance_after = balance_after + 0.01 "
            f"WHERE id = (SELECT MIN(id) FROM cash_ledger_entries WHERE account_id = {s.alice} "
            "AND entry_type = 'TRADE_DEBIT')"
        ),
        {"ledger_replay"},
        lambda s, f: f.ids.get("account_id") == s.alice and f.field == "balance_after",
    ),
    Fault(
        "ledger entry deleted",
        lambda s: (
            "DELETE FROM cash_ledger_entries WHERE id = (SELECT MIN(id) FROM cash_ledger_entries "
            f"WHERE account_id = {s.bob} AND entry_type = 'RELEASE')"
        ),
        {"ledger_replay"},
        lambda s, f: f.ids.get("account_id") == s.bob and f.field == "reserved",
    ),
    Fault(
        "cash balance changed behind the ledger's back",
        lambda s: f"UPDATE cash_balances SET balance = balance + 100 WHERE account_id = {s.alice}",
        # The replay sees the balance disagree with the ledger, and the extra cash breaks conservation.
        {"ledger_replay", "cash_and_shares_conserved"},
        lambda s, f: f.ids.get("account_id") == s.alice or f.field == "total_cash",
    ),
    Fault(
        "open buy order repriced below its reservation",
        lambda s: f"UPDATE orders SET price = 248.50 WHERE id = {s.mm_tsla_bid}",
        {"reservations_match_open_orders"},
        lambda s, f: f.ids.get("account_id") == s.market_maker and f.field == "reserved",
    ),
    Fault(
        "reserved shares inflated",
        lambda s: (
            "UPDATE positions SET reserved_quantity = reserved_quantity + 1 "
            f"WHERE account_id = {s.market_maker} AND instrument_id = {s.tsla}"
        ),
        {"reservations_match_open_orders"},
        lambda s, f: f.ids.get("account_id") == s.market_maker and f.ids.get("instrument_id") == s.tsla,
    ),
    Fault(
        "filled quantity lowered on a filled order",
        lambda s: f"UPDATE orders SET filled_quantity = filled_quantity - 1 WHERE id = {s.mm_aapl_ask}",
        {"order_fills_match_trades"},
        lambda s, f: f.ids.get("order_id") == s.mm_aapl_ask,
    ),
    Fault(
        "trade price moved outside the buy limit",
        lambda s: f"UPDATE trades SET price = 192.50 WHERE id = {s.trade_alice_buys}",
        {"trades_are_valid"},
        lambda s, f: f.ids.get("trade_id") == s.trade_alice_buys and f.field == "price",
    ),
    Fault(
        "shares moved between two accounts without a trade",
        lambda s: (
            f"UPDATE positions SET quantity = quantity + 5 WHERE account_id = {s.alice} "
            f"AND instrument_id = {s.aapl}; "
            f"UPDATE positions SET quantity = quantity - 5 WHERE account_id = {s.market_maker} "
            f"AND instrument_id = {s.aapl}"
        ),
        {"positions_match_trades"},
        lambda s, f: (
            f.ids.get("instrument_id") == s.aapl and f.ids.get("account_id") in (s.alice, s.market_maker)
        ),
    ),
    Fault(
        "shares created out of nothing",
        lambda s: (
            f"UPDATE positions SET quantity = quantity + 5 WHERE account_id = {s.alice} "
            f"AND instrument_id = {s.aapl}"
        ),
        # One position no longer matches its trades, and the instrument's total no longer matches the grant.
        {"positions_match_trades", "cash_and_shares_conserved"},
        lambda s, f: f.ids.get("instrument_id") == s.aapl,
    ),
    Fault(
        "resting ask repriced through the best bid",
        lambda s: f"UPDATE orders SET price = 248.00 WHERE id = {s.mm_tsla_ask}",
        {"open_book_not_crossed"},
        lambda s, f: f.ids.get("instrument_id") == s.tsla and str(s.mm_tsla_ask) in f.message,
    ),
]


@pytest.mark.parametrize("fault", FAULTS, ids=[fault.name for fault in FAULTS])
def test_fault_is_caught_by_exactly_the_right_checks(write_engine, scenario, domain_settings, fault):
    with write_engine.connect() as connection:
        for statement in fault.sql(scenario).split(";"):
            if statement.strip():
                connection.execute(text(statement))

    results = run_checks(write_engine, domain_settings)

    failed = {name for name, result in results.items() if not result.passed}
    assert failed == fault.fails, {name: [f.message for f in results[name].findings] for name in failed}
    for name in fault.fails:
        findings = results[name].findings
        assert findings, name
        assert all(fault.points_at(scenario, finding) for finding in findings), [f.message for f in findings]
        assert all(finding.expected != finding.actual for finding in findings)

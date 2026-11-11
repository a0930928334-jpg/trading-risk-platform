"""Check 2: reservations equal what the open orders hold."""

from __future__ import annotations

from collections import defaultdict
from decimal import Decimal

from trading_analytics.checks.base import CheckResult, Finding, Severity
from trading_analytics.extract import Dataset

NAME = "reservations_match_open_orders"
DESCRIPTION = (
    "Each account's reserved cash equals the sum of remaining quantity x limit price over its open buy "
    "orders, and each position's reserved quantity equals the remaining quantity of its open sell orders"
)


def run(dataset: Dataset) -> CheckResult:
    findings: list[Finding] = []
    open_orders = dataset.open_orders

    # A buy order reserves quantity x limit price at placement and releases price improvement at once,
    # so an open buy order always holds exactly remaining x limit (ADR 0002).
    expected_cash: dict[int, Decimal] = defaultdict(Decimal)
    expected_shares: dict[tuple[int, int], int] = defaultdict(int)
    for order in open_orders.itertuples(index=False):
        remaining = int(order.quantity) - int(order.filled_quantity)
        if order.side == "BUY":
            expected_cash[int(order.account_id)] += Decimal(order.price) * remaining
        else:
            expected_shares[(int(order.account_id), int(order.instrument_id))] += remaining

    for cash in dataset.cash_balances.itertuples(index=False):
        account_id = int(cash.account_id)
        expected = expected_cash.pop(account_id, Decimal(0))
        if Decimal(cash.reserved) != expected:
            findings.append(
                Finding(
                    "account",
                    {"account_id": account_id},
                    "reserved",
                    expected,
                    Decimal(cash.reserved),
                    f"Account {account_id} has {cash.reserved} reserved cash; "
                    f"its open buy orders hold {expected}",
                )
            )
    for account_id, expected in expected_cash.items():
        findings.append(
            Finding(
                "account",
                {"account_id": account_id},
                "reserved",
                expected,
                None,
                f"Account {account_id} has open buy orders holding {expected} but no cash balance row",
            )
        )

    for position in dataset.positions.itertuples(index=False):
        key = (int(position.account_id), int(position.instrument_id))
        expected = expected_shares.pop(key, 0)
        if int(position.reserved_quantity) != expected:
            findings.append(
                Finding(
                    "position",
                    {"account_id": key[0], "instrument_id": key[1], "position_id": int(position.id)},
                    "reserved_quantity",
                    expected,
                    int(position.reserved_quantity),
                    f"Position of account {key[0]} in instrument {key[1]} reserves "
                    f"{position.reserved_quantity} "
                    f"shares; its open sell orders hold {expected}",
                )
            )
    for (account_id, instrument_id), expected in expected_shares.items():
        findings.append(
            Finding(
                "position",
                {"account_id": account_id, "instrument_id": instrument_id},
                "reserved_quantity",
                expected,
                None,
                f"Account {account_id} has open sell orders for {expected} shares of instrument "
                f"{instrument_id} but no position row",
            )
        )

    rows = len(dataset.cash_balances) + len(dataset.positions)
    return CheckResult(NAME, DESCRIPTION, Severity.HIGH, rows, findings)

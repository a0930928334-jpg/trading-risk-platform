"""Check 7: the open orders in the database never form a crossed book."""

from __future__ import annotations

from decimal import Decimal

from trading_analytics.checks.base import CheckResult, Finding, Severity
from trading_analytics.extract import Dataset

NAME = "open_book_not_crossed"
DESCRIPTION = "For every instrument the highest open bid is below the lowest open ask"


def run(dataset: Dataset) -> CheckResult:
    findings: list[Finding] = []
    open_orders = dataset.open_orders

    for instrument_id, orders in open_orders.groupby("instrument_id"):
        bids = orders[orders["side"] == "BUY"]
        asks = orders[orders["side"] == "SELL"]
        if bids.empty or asks.empty:
            continue
        best_bid = max(Decimal(price) for price in bids["price"])
        best_ask = min(Decimal(price) for price in asks["price"])
        if best_bid >= best_ask:
            # Every resting order at or through the opposite best price should have matched.
            crossing = sorted(
                int(order_id)
                for order_id in [
                    *bids[bids["price"] >= best_ask]["id"],
                    *asks[asks["price"] <= best_bid]["id"],
                ]
            )
            findings.append(
                Finding(
                    "instrument",
                    {"instrument_id": int(instrument_id)},
                    "best_bid",
                    f"below {best_ask}",
                    best_bid,
                    f"Instrument {instrument_id}: best bid {best_bid} is not below best ask {best_ask}; "
                    f"crossing open orders: {crossing}",
                )
            )

    return CheckResult(NAME, DESCRIPTION, Severity.HIGH, len(open_orders), findings)

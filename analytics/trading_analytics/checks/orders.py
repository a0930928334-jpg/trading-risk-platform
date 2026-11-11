"""Check 3: filled quantities and statuses agree with the trades."""

from __future__ import annotations

from trading_analytics.checks.base import CheckResult, Finding, Severity
from trading_analytics.extract import Dataset

NAME = "order_fills_match_trades"
DESCRIPTION = (
    "Each order's filled_quantity equals the quantity of its trades, and its status matches the fill: "
    "NEW and REJECTED have none, PARTIALLY_FILLED some, FILLED all, CANCELLED less than all"
)


def _status_consistent(status: str, filled: int, quantity: int) -> bool:
    match status:
        case "NEW" | "REJECTED":
            return filled == 0
        case "PARTIALLY_FILLED":
            return 0 < filled < quantity
        case "FILLED":
            return filled == quantity
        case "CANCELLED":
            return filled < quantity
    return False


def run(dataset: Dataset) -> CheckResult:
    findings: list[Finding] = []
    trades = dataset.trades
    traded = (
        trades.groupby("buy_order_id")["quantity"]
        .sum()
        .add(trades.groupby("sell_order_id")["quantity"].sum(), fill_value=0)
    ).to_dict()

    for order in dataset.orders.itertuples(index=False):
        order_id = int(order.id)
        ids = {"order_id": order_id, "account_id": int(order.account_id)}
        filled = int(order.filled_quantity)
        from_trades = int(traded.get(order_id, 0))
        if filled != from_trades:
            findings.append(
                Finding(
                    "order",
                    ids,
                    "filled_quantity",
                    from_trades,
                    filled,
                    f"Order {order_id} has filled_quantity {filled} but its trades add up to {from_trades}",
                )
            )
        if not _status_consistent(order.status, filled, int(order.quantity)):
            findings.append(
                Finding(
                    "order",
                    ids,
                    "status",
                    f"status consistent with {filled}/{order.quantity} filled",
                    order.status,
                    f"Order {order_id} is {order.status} with {filled} of {order.quantity} filled",
                )
            )

    return CheckResult(NAME, DESCRIPTION, Severity.HIGH, len(dataset.orders), findings)

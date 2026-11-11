"""Check 4: every trade is a legal execution of its two orders."""

from __future__ import annotations

from decimal import Decimal

from trading_analytics.checks.base import CheckResult, Finding, Severity
from trading_analytics.extract import Dataset

NAME = "trades_are_valid"
DESCRIPTION = (
    "Each trade's price lies between the sell order's and the buy order's limit and equals the resting "
    "(maker) order's price, the orders have the right sides, accounts and instrument, and the buyer is "
    "not the seller"
)


def run(dataset: Dataset) -> CheckResult:
    findings: list[Finding] = []
    orders = dataset.orders.set_index("id")

    for trade in dataset.trades.itertuples(index=False):
        trade_id = int(trade.id)
        ids = {
            "trade_id": trade_id,
            "buy_order_id": int(trade.buy_order_id),
            "sell_order_id": int(trade.sell_order_id),
        }
        price = Decimal(trade.price)

        if int(trade.buyer_account_id) == int(trade.seller_account_id):
            findings.append(
                Finding(
                    "trade",
                    ids,
                    "seller_account_id",
                    f"not {trade.buyer_account_id}",
                    int(trade.seller_account_id),
                    f"Trade {trade_id} has the same buyer and seller, account {trade.buyer_account_id}",
                )
            )

        sides = {"buy_order_id": ("BUY", "buyer_account_id"), "sell_order_id": ("SELL", "seller_account_id")}
        legs = {}
        for column, (side, account_column) in sides.items():
            order_id = int(getattr(trade, column))
            if order_id not in orders.index:
                findings.append(
                    Finding(
                        "trade",
                        ids,
                        column,
                        "an existing order",
                        order_id,
                        f"Trade {trade_id} references missing order {order_id}",
                    )
                )
                continue
            order = orders.loc[order_id]
            legs[side] = order
            if order["side"] != side:
                findings.append(
                    Finding(
                        "trade",
                        ids,
                        f"{column}.side",
                        side,
                        order["side"],
                        f"Trade {trade_id} uses order {order_id} as its {side} leg "
                        f"but the order is a {order['side']}",
                    )
                )
            if int(order["account_id"]) != int(getattr(trade, account_column)):
                findings.append(
                    Finding(
                        "trade",
                        ids,
                        account_column,
                        int(order["account_id"]),
                        int(getattr(trade, account_column)),
                        f"Trade {trade_id} names account {getattr(trade, account_column)} "
                        f"as {account_column} "
                        f"but order {order_id} belongs to account {order['account_id']}",
                    )
                )
            if int(order["instrument_id"]) != int(trade.instrument_id):
                findings.append(
                    Finding(
                        "trade",
                        ids,
                        "instrument_id",
                        int(order["instrument_id"]),
                        int(trade.instrument_id),
                        f"Trade {trade_id} is in instrument {trade.instrument_id} but order {order_id} is in "
                        f"instrument {order['instrument_id']}",
                    )
                )

        if "BUY" in legs and "SELL" in legs:
            buy_limit = Decimal(legs["BUY"]["price"])
            sell_limit = Decimal(legs["SELL"]["price"])
            if not sell_limit <= price <= buy_limit:
                findings.append(
                    Finding(
                        "trade",
                        ids,
                        "price",
                        f"between {sell_limit} and {buy_limit}",
                        price,
                        f"Trade {trade_id} at {price} is outside the sell limit {sell_limit} "
                        f"and buy limit {buy_limit}",
                    )
                )
            # Trades execute at the resting order's price; the aggressor is the incoming order.
            maker = legs["SELL"] if trade.aggressor_side == "BUY" else legs["BUY"]
            maker_price = Decimal(maker["price"])
            if price != maker_price:
                findings.append(
                    Finding(
                        "trade",
                        ids,
                        "price",
                        maker_price,
                        price,
                        f"Trade {trade_id} at {price} does not equal the resting order's price {maker_price}",
                    )
                )

    return CheckResult(NAME, DESCRIPTION, Severity.CRITICAL, len(dataset.trades), findings)

"""Per-account behavioural features for anomaly detection.

System (market maker) accounts are excluded: they are expected to look unlike every user account.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from trading_analytics.config import DomainSettings
from trading_analytics.extract import Dataset

FEATURE_COLUMNS = [
    "orders",
    "reject_rate",
    "user_cancel_rate",
    "orders_per_trade",
    "max_notional_ratio",
    "mean_notional_ratio",
    "orders_per_active_minute",
    "median_seconds_to_cancel",
]

FEATURE_DESCRIPTIONS = {
    "orders": "Orders placed, including rejected ones",
    "reject_rate": "Share of orders the risk rules rejected",
    "user_cancel_rate": "Share of accepted orders the user cancelled",
    "orders_per_trade": (
        "Accepted orders per trade the account took part in (accepted orders when it never traded)"
    ),
    "max_notional_ratio": "Largest order value divided by the maximum order value the risk rules allow",
    "mean_notional_ratio": "Mean order value divided by the maximum order value the risk rules allow",
    "orders_per_active_minute": "Orders per minute between the account's first and last order",
    "median_seconds_to_cancel": "Median time a user-cancelled order rested before the cancellation",
}


def build(dataset: Dataset, settings: DomainSettings) -> pd.DataFrame:
    orders = dataset.orders[~dataset.orders["account_id"].isin(dataset.system_account_ids)].copy()
    if orders.empty:
        return pd.DataFrame(columns=["account_id", *FEATURE_COLUMNS]).set_index("account_id")

    orders["notional"] = orders["price"].astype(float) * orders["quantity"].astype(int)
    orders["rejected"] = orders["status"] == "REJECTED"
    orders["accepted"] = ~orders["rejected"]
    orders["user_cancelled"] = (orders["status"] == "CANCELLED") & orders["reject_code"].isna()
    orders["seconds_alive"] = (orders["updated_at"] - orders["created_at"]).dt.total_seconds().clip(lower=0)
    cap = float(settings.max_order_notional)

    grouped = orders.groupby("account_id", sort=True)
    accepted = grouped["accepted"].sum()
    trades = dataset.trades
    trade_legs = pd.concat([trades["buyer_account_id"], trades["seller_account_id"]]).value_counts()
    trades_per_account = trade_legs.reindex(accepted.index).fillna(0)
    span_minutes = (grouped["created_at"].max() - grouped["created_at"].min()).dt.total_seconds() / 60

    features = pd.DataFrame(
        {
            "orders": grouped.size().astype(float),
            "reject_rate": grouped["rejected"].mean(),
            "user_cancel_rate": (grouped["user_cancelled"].sum() / accepted.replace(0, np.nan)).fillna(0.0),
            # An account that never traded gets its accepted order count: every order went untraded.
            "orders_per_trade": accepted / trades_per_account.replace(0, 1),
            "max_notional_ratio": grouped["notional"].max() / cap,
            "mean_notional_ratio": grouped["notional"].mean() / cap,
            "orders_per_active_minute": grouped.size() / (span_minutes + 1),
            "median_seconds_to_cancel": orders[orders["user_cancelled"]]
            .groupby("account_id")["seconds_alive"]
            .median()
            .reindex(accepted.index),
        }
    )
    # Accounts that cancelled nothing have no cancel latency; the longest observed latency is the most
    # "normal" value, which keeps them away from the fast-cancelling tail.
    fill_value = (
        features["median_seconds_to_cancel"].max()
        if features["median_seconds_to_cancel"].notna().any()
        else 0.0
    )
    features["median_seconds_to_cancel"] = features["median_seconds_to_cancel"].fillna(fill_value)
    features.index.name = "account_id"
    return features[FEATURE_COLUMNS].astype(float)

"""Quality metrics over the order and trade flow.

Everything here is descriptive: counts, rates and distributions that a reviewer can compare run to run.
Rates are fractions in [0, 1]; the report formats them as percentages.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import numpy as np
import pandas as pd

from trading_analytics.extract import OPEN_STATUSES, Dataset

SELF_TRADE_PREVENTED = "SELF_TRADE_PREVENTED"


@dataclass
class Metrics:
    bucket: str
    overview: dict[str, Any]
    by_reject_code: pd.DataFrame
    by_instrument: pd.DataFrame
    time_to_first_fill: pd.DataFrame
    by_account: pd.DataFrame
    over_time: pd.DataFrame

    def to_dict(self) -> dict[str, Any]:
        return {
            "bucket": self.bucket,
            "overview": self.overview,
            "by_reject_code": _records(self.by_reject_code),
            "by_instrument": _records(self.by_instrument),
            "time_to_first_fill": _records(self.time_to_first_fill),
            "by_account": _records(self.by_account),
            "over_time": _records(self.over_time),
        }


def compute(dataset: Dataset, bucket: str | None = None) -> Metrics:
    orders = _enrich_orders(dataset)
    bucket = bucket or _bucket_for(orders)
    trades = dataset.trades.copy()
    trades["notional"] = trades["price"].astype(float) * trades["quantity"].astype(int)

    return Metrics(
        bucket=bucket,
        overview=_overview(orders, trades),
        by_reject_code=_by_reject_code(orders),
        by_instrument=_by_instrument(orders, trades),
        time_to_first_fill=_time_to_first_fill(orders, dataset.trades),
        by_account=_by_account(orders, trades, dataset),
        over_time=_over_time(orders, trades, bucket),
    )


def _bucket_for(orders: pd.DataFrame) -> str:
    """Time bucket for the flow series: fine enough that a short load-generator run still shows a shape."""
    if orders.empty:
        return "1min"
    span = orders["created_at"].max() - orders["created_at"].min()
    if span <= pd.Timedelta(minutes=15):
        return "10s"
    if span <= pd.Timedelta(hours=6):
        return "1min"
    return "1h"


def _enrich_orders(dataset: Dataset) -> pd.DataFrame:
    orders = dataset.orders.merge(
        dataset.instruments[["id", "symbol"]].rename(columns={"id": "instrument_id"}),
        on="instrument_id",
        how="left",
    )
    orders["notional"] = orders["price"].astype(float) * orders["quantity"].astype(int)
    orders["rejected"] = orders["status"] == "REJECTED"
    orders["accepted"] = ~orders["rejected"]
    orders["filled"] = orders["status"] == "FILLED"
    orders["open"] = orders["status"].isin(OPEN_STATUSES)
    # The matching engine cancels the remainder of an order that would trade with the same account's
    # resting order; those cancellations are the system's, not the user's.
    orders["user_cancelled"] = (orders["status"] == "CANCELLED") & orders["reject_code"].isna()
    orders["self_trade_cancelled"] = (orders["status"] == "CANCELLED") & (
        orders["reject_code"] == SELF_TRADE_PREVENTED
    )
    orders["traded"] = orders["filled_quantity"].astype(int) > 0
    return orders


def _rate(numerator: Any, denominator: Any) -> float:
    return float(numerator) / float(denominator) if float(denominator) else 0.0


def _overview(orders: pd.DataFrame, trades: pd.DataFrame) -> dict[str, Any]:
    accepted = int(orders["accepted"].sum())
    return {
        "orders": len(orders),
        "accepted_orders": accepted,
        "rejected_orders": int(orders["rejected"].sum()),
        "reject_rate": _rate(orders["rejected"].sum(), len(orders)),
        "fill_rate": _rate(orders["filled"].sum(), accepted),
        "traded_rate": _rate(orders["traded"].sum(), accepted),
        "user_cancel_rate": _rate(orders["user_cancelled"].sum(), accepted),
        "self_trade_cancellations": int(orders["self_trade_cancelled"].sum()),
        "open_orders": int(orders["open"].sum()),
        "trades": len(trades),
        "traded_shares": int(trades["quantity"].astype(int).sum()) if len(trades) else 0,
        "traded_notional": float(trades["notional"].sum()) if len(trades) else 0.0,
        "first_order_at": _iso(orders["created_at"].min()) if len(orders) else None,
        "last_order_at": _iso(orders["created_at"].max()) if len(orders) else None,
    }


def _by_reject_code(orders: pd.DataFrame) -> pd.DataFrame:
    """Rejections per risk rule: which rules fire, and how much of the flow each one stops."""
    rejected = orders[orders["rejected"]]
    if rejected.empty:
        return pd.DataFrame(columns=["reject_code", "orders", "share_of_rejections", "rate_of_all_orders"])
    counts = rejected.groupby("reject_code").size().rename("orders").reset_index()
    counts["share_of_rejections"] = counts["orders"] / counts["orders"].sum()
    counts["rate_of_all_orders"] = counts["orders"] / len(orders)
    return counts.sort_values("orders", ascending=False).reset_index(drop=True)


def _by_instrument(orders: pd.DataFrame, trades: pd.DataFrame) -> pd.DataFrame:
    grouped = orders.groupby("symbol", sort=True)
    table = pd.DataFrame(
        {
            "orders": grouped.size(),
            "accepted": grouped["accepted"].sum(),
            "reject_rate": grouped["rejected"].mean(),
            "fill_rate": grouped["filled"].sum() / grouped["accepted"].sum().replace(0, np.nan),
            "user_cancel_rate": grouped["user_cancelled"].sum()
            / grouped["accepted"].sum().replace(0, np.nan),
            "open_orders": grouped["open"].sum(),
        }
    ).fillna(0.0)
    symbol_of = orders.drop_duplicates("instrument_id").set_index("instrument_id")["symbol"]
    trades_by_symbol = trades.assign(symbol=trades["instrument_id"].map(symbol_of)).groupby("symbol")
    table["trades"] = trades_by_symbol.size()
    table["traded_shares"] = trades_by_symbol["quantity"].sum()
    table["traded_notional"] = trades_by_symbol["notional"].sum()
    return table.fillna(0).reset_index()


def _time_to_first_fill(orders: pd.DataFrame, trades: pd.DataFrame) -> pd.DataFrame:
    """Seconds from placement to the first trade, for orders that traded.

    A taker fills in the transaction that places it, so its time is close to zero; a resting (maker)
    order waits until someone crosses it. Both populations are reported separately.
    """
    if trades.empty:
        return pd.DataFrame(columns=["role", "orders", "p50_seconds", "p90_seconds", "max_seconds"])
    legs = pd.concat(
        [
            trades[["buy_order_id", "created_at", "aggressor_side"]]
            .rename(columns={"buy_order_id": "order_id"})
            .assign(role=lambda t: np.where(t["aggressor_side"] == "BUY", "taker", "maker")),
            trades[["sell_order_id", "created_at", "aggressor_side"]]
            .rename(columns={"sell_order_id": "order_id"})
            .assign(role=lambda t: np.where(t["aggressor_side"] == "SELL", "taker", "maker")),
        ]
    )
    first = legs.sort_values("created_at").drop_duplicates("order_id", keep="first")
    first = first.merge(
        orders[["id", "created_at"]].rename(columns={"id": "order_id", "created_at": "placed_at"})
    )
    first["seconds"] = (first["created_at"] - first["placed_at"]).dt.total_seconds().clip(lower=0)
    rows = []
    for role in ("taker", "maker"):
        seconds = first.loc[first["role"] == role, "seconds"]
        if seconds.empty:
            continue
        rows.append(
            {
                "role": role,
                "orders": len(seconds),
                "p50_seconds": float(seconds.quantile(0.5)),
                "p90_seconds": float(seconds.quantile(0.9)),
                "max_seconds": float(seconds.max()),
            }
        )
    return pd.DataFrame(rows)


def _by_account(orders: pd.DataFrame, trades: pd.DataFrame, dataset: Dataset) -> pd.DataFrame:
    grouped = orders.groupby("account_id", sort=True)
    table = pd.DataFrame(
        {
            "orders": grouped.size(),
            "accepted": grouped["accepted"].sum(),
            "rejected": grouped["rejected"].sum(),
            "user_cancelled": grouped["user_cancelled"].sum(),
        }
    )
    trade_legs = pd.concat([trades["buyer_account_id"], trades["seller_account_id"]]).value_counts()
    table["trades"] = trade_legs.reindex(table.index).fillna(0).astype(int)
    table["orders_per_trade"] = table["accepted"] / table["trades"].replace(0, np.nan)
    table["reject_rate"] = table["rejected"] / table["orders"]
    table["user_cancel_rate"] = table["user_cancelled"] / table["accepted"].replace(0, np.nan)
    table = table.reset_index()
    table["system_account"] = table["account_id"].isin(dataset.system_account_ids)
    return table


def _over_time(orders: pd.DataFrame, trades: pd.DataFrame, bucket: str) -> pd.DataFrame:
    """Flow per time bucket, so a reviewer can see when rejections or cancellations spiked."""
    if orders.empty:
        return pd.DataFrame(
            columns=["bucket", "orders", "rejected", "user_cancelled", "trades", "reject_rate"]
        )
    by_bucket = orders.groupby(orders["created_at"].dt.floor(bucket))
    table = pd.DataFrame(
        {
            "orders": by_bucket.size(),
            "rejected": by_bucket["rejected"].sum(),
            "user_cancelled": by_bucket["user_cancelled"].sum(),
        }
    )
    if not trades.empty:
        table["trades"] = trades.groupby(trades["created_at"].dt.floor(bucket)).size()
    else:
        table["trades"] = 0
    table = table.fillna(0).astype(int)
    table["reject_rate"] = table["rejected"] / table["orders"]
    table.index.name = "bucket"
    return table.reset_index()


def _records(frame: pd.DataFrame) -> list[dict[str, Any]]:
    records = []
    for record in frame.to_dict(orient="records"):
        records.append({key: _plain(value) for key, value in record.items()})
    return records


def _plain(value: Any) -> Any:
    if isinstance(value, pd.Timestamp):
        return value.isoformat()
    if isinstance(value, np.generic):
        return value.item()
    if isinstance(value, float) and np.isnan(value):
        return None
    return value


def _iso(value: Any) -> str | None:
    return None if pd.isna(value) else pd.Timestamp(value).isoformat()

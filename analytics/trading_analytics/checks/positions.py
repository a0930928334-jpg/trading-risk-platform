"""Check 6: every position is the net of the account's trades (plus the system account's grant)."""

from __future__ import annotations

from collections import defaultdict

from trading_analytics.checks.base import CheckResult, Finding, Severity
from trading_analytics.config import DomainSettings
from trading_analytics.extract import Dataset

NAME = "positions_match_trades"
DESCRIPTION = (
    "Each position's quantity equals shares bought minus shares sold in that instrument, plus the "
    "initial grant for system (market maker) accounts"
)


def run(dataset: Dataset, settings: DomainSettings) -> CheckResult:
    findings: list[Finding] = []
    system_accounts = dataset.system_account_ids
    grant = settings.market_maker_shares_per_instrument

    net: dict[tuple[int, int], int] = defaultdict(int)
    for trade in dataset.trades.itertuples(index=False):
        quantity = int(trade.quantity)
        instrument_id = int(trade.instrument_id)
        net[(int(trade.buyer_account_id), instrument_id)] += quantity
        net[(int(trade.seller_account_id), instrument_id)] -= quantity

    seen: set[tuple[int, int]] = set()
    for position in dataset.positions.itertuples(index=False):
        key = (int(position.account_id), int(position.instrument_id))
        seen.add(key)
        expected = net.get(key, 0) + (grant if key[0] in system_accounts else 0)
        if int(position.quantity) != expected:
            findings.append(
                Finding(
                    "position",
                    {"account_id": key[0], "instrument_id": key[1], "position_id": int(position.id)},
                    "quantity",
                    expected,
                    int(position.quantity),
                    f"Account {key[0]} holds {position.quantity} of instrument {key[1]}; "
                    f"its trades net to {expected}",
                )
            )

    for key, expected in net.items():
        if key not in seen and expected != 0:
            findings.append(
                Finding(
                    "position",
                    {"account_id": key[0], "instrument_id": key[1]},
                    "quantity",
                    expected,
                    None,
                    f"Account {key[0]} traded instrument {key[1]} (net {expected}) but has no position row",
                )
            )

    return CheckResult(NAME, DESCRIPTION, Severity.CRITICAL, len(dataset.positions), findings)

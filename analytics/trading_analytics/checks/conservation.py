"""Check 5: trading moves cash and shares between accounts but never creates or destroys them."""

from __future__ import annotations

from decimal import Decimal

from trading_analytics.checks.base import CheckResult, Finding, Severity
from trading_analytics.config import DomainSettings
from trading_analytics.extract import Dataset

NAME = "cash_and_shares_conserved"
DESCRIPTION = (
    "Total cash across all accounts equals the sum of all deposits, and the total shares of each instrument "
    "equal the shares granted to the system (market maker) accounts when they were created"
)


def run(dataset: Dataset, settings: DomainSettings) -> CheckResult:
    findings: list[Finding] = []

    deposits = dataset.cash_ledger_entries[dataset.cash_ledger_entries["entry_type"] == "DEPOSIT"]
    total_deposits = sum((Decimal(amount) for amount in deposits["amount"]), Decimal(0))
    total_cash = sum((Decimal(balance) for balance in dataset.cash_balances["balance"]), Decimal(0))
    if total_cash != total_deposits:
        findings.append(
            Finding(
                "system",
                {},
                "total_cash",
                total_deposits,
                total_cash,
                f"Accounts hold {total_cash} in total but {total_deposits} was deposited",
            )
        )

    # Deposits are the only way cash enters; the only way shares enter is the one-off grant to each
    # SYSTEM account, which the application configures rather than records.
    grant = settings.market_maker_shares_per_instrument * len(dataset.system_account_ids)
    held = dataset.positions.groupby("instrument_id")["quantity"].sum()
    for instrument in dataset.instruments.itertuples(index=False):
        instrument_id = int(instrument.id)
        total_shares = int(held.get(instrument_id, 0))
        if total_shares != grant:
            findings.append(
                Finding(
                    "instrument",
                    {"instrument_id": instrument_id},
                    "total_shares",
                    grant,
                    total_shares,
                    f"{instrument.symbol}: accounts hold {total_shares} shares in total "
                    f"but {grant} were granted",
                )
            )

    rows = len(dataset.cash_balances) + len(dataset.positions)
    return CheckResult(NAME, DESCRIPTION, Severity.CRITICAL, rows, findings)

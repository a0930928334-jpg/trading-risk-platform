"""Check 1: the cash ledger replays to every balance."""

from __future__ import annotations

from decimal import Decimal

from trading_analytics.checks.base import CheckResult, Finding, Severity
from trading_analytics.extract import Dataset

NAME = "ledger_replay"
DESCRIPTION = (
    "Replaying each account's cash ledger in id order reproduces every balance_after and reserved_after "
    "snapshot and ends at the account's current balance and reserved amount"
)

# Mirrors CashBalance in the Java code: DEPOSIT and TRADE_CREDIT add to the balance, RESERVE and RELEASE
# move the reservation, TRADE_DEBIT pays out of reserved cash and so lowers both.
BALANCE_DELTA = {"DEPOSIT": 1, "TRADE_CREDIT": 1, "TRADE_DEBIT": -1, "RESERVE": 0, "RELEASE": 0}
RESERVED_DELTA = {"DEPOSIT": 0, "TRADE_CREDIT": 0, "TRADE_DEBIT": -1, "RESERVE": 1, "RELEASE": -1}


def run(dataset: Dataset) -> CheckResult:
    findings: list[Finding] = []
    replayed: dict[int, tuple[Decimal, Decimal]] = {}

    ledger = dataset.cash_ledger_entries.sort_values(["account_id", "id"])
    for account_id, entries in ledger.groupby("account_id", sort=True):
        balance = Decimal(0)
        reserved = Decimal(0)
        for entry in entries.itertuples(index=False):
            amount = Decimal(entry.amount)
            balance += BALANCE_DELTA[entry.entry_type] * amount
            reserved += RESERVED_DELTA[entry.entry_type] * amount
            ids = {"account_id": int(account_id), "ledger_entry_id": int(entry.id)}
            if Decimal(entry.balance_after) != balance:
                findings.append(
                    Finding(
                        "ledger_entry",
                        ids,
                        "balance_after",
                        balance,
                        Decimal(entry.balance_after),
                        f"Ledger entry {entry.id} ({entry.entry_type} {amount}) records balance_after "
                        f"{entry.balance_after}; the entries before it add up to {balance}",
                    )
                )
            if Decimal(entry.reserved_after) != reserved:
                findings.append(
                    Finding(
                        "ledger_entry",
                        ids,
                        "reserved_after",
                        reserved,
                        Decimal(entry.reserved_after),
                        f"Ledger entry {entry.id} ({entry.entry_type} {amount}) records reserved_after "
                        f"{entry.reserved_after}; the entries before it add up to {reserved}",
                    )
                )
        replayed[int(account_id)] = (balance, reserved)

    for cash in dataset.cash_balances.itertuples(index=False):
        account_id = int(cash.account_id)
        balance, reserved = replayed.get(account_id, (Decimal(0), Decimal(0)))
        ids = {"account_id": account_id}
        if Decimal(cash.balance) != balance:
            findings.append(
                Finding(
                    "account",
                    ids,
                    "balance",
                    balance,
                    Decimal(cash.balance),
                    f"Account {account_id} has balance {cash.balance} but its ledger replays to {balance}",
                )
            )
        if Decimal(cash.reserved) != reserved:
            findings.append(
                Finding(
                    "account",
                    ids,
                    "reserved",
                    reserved,
                    Decimal(cash.reserved),
                    f"Account {account_id} has reserved {cash.reserved} but its ledger replays to {reserved}",
                )
            )

    rows = len(ledger) + len(dataset.cash_balances)
    return CheckResult(NAME, DESCRIPTION, Severity.CRITICAL, rows, findings)

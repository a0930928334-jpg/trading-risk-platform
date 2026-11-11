"""The seven invariants the trading system must always satisfy, each as a structured check."""

from __future__ import annotations

from trading_analytics.checks import book, conservation, ledger, orders, positions, reservations, trades
from trading_analytics.checks.base import CheckResult, Finding, Severity
from trading_analytics.config import DomainSettings
from trading_analytics.extract import Dataset

CHECK_NAMES = (
    ledger.NAME,
    reservations.NAME,
    orders.NAME,
    trades.NAME,
    conservation.NAME,
    positions.NAME,
    book.NAME,
)


def run_all(dataset: Dataset, settings: DomainSettings) -> list[CheckResult]:
    return [
        ledger.run(dataset),
        reservations.run(dataset),
        orders.run(dataset),
        trades.run(dataset),
        conservation.run(dataset, settings),
        positions.run(dataset, settings),
        book.run(dataset),
    ]


def failed(results: list[CheckResult]) -> list[CheckResult]:
    return [result for result in results if not result.passed]


__all__ = ["CHECK_NAMES", "CheckResult", "Finding", "Severity", "failed", "run_all"]

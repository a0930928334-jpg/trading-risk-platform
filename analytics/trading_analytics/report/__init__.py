"""Writes the HTML report and the JSON summary of one analytics run."""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from trading_analytics.anomaly import AnomalyReport
from trading_analytics.checks import CheckResult
from trading_analytics.extract import Dataset
from trading_analytics.metrics import Metrics
from trading_analytics.report import html

HTML_NAME = "report.html"
JSON_NAME = "summary.json"
MAX_FINDINGS_IN_SUMMARY = 200


@dataclass
class RunResult:
    generated_at: datetime
    database: str
    row_counts: dict[str, int]
    checks: list[CheckResult]
    metrics: Metrics
    anomalies: AnomalyReport

    @property
    def passed(self) -> bool:
        return all(check.passed for check in self.checks)

    def summary(self) -> dict[str, Any]:
        return {
            "generated_at": self.generated_at.isoformat(),
            "database": self.database,
            "passed": self.passed,
            "row_counts": self.row_counts,
            "checks": [check.to_dict(MAX_FINDINGS_IN_SUMMARY) for check in self.checks],
            "metrics": self.metrics.to_dict(),
            "anomalies": self.anomalies.to_dict(),
        }


def build(
    dataset: Dataset, database: str, checks: list[CheckResult], metrics: Metrics, anomalies: AnomalyReport
) -> RunResult:
    return RunResult(datetime.now(UTC), database, dataset.row_counts(), checks, metrics, anomalies)


def write(result: RunResult, out_dir: Path) -> tuple[Path, Path]:
    out_dir.mkdir(parents=True, exist_ok=True)
    html_path = out_dir / HTML_NAME
    json_path = out_dir / JSON_NAME
    html_path.write_text(html.render(result), encoding="utf-8")
    json_path.write_text(json.dumps(result.summary(), indent=2), encoding="utf-8")
    return html_path, json_path

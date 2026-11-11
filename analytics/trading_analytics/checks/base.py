"""Structured results shared by every data quality check."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import asdict, dataclass, field
from decimal import Decimal
from enum import StrEnum
from typing import Any


class Severity(StrEnum):
    # Money or shares created, destroyed or misattributed.
    CRITICAL = "critical"
    # State that the application relies on for later decisions (risk checks, matching) is inconsistent.
    HIGH = "high"


@dataclass(frozen=True)
class Finding:
    """One violated expectation, with enough identifiers to open the offending rows."""

    entity: str
    ids: Mapping[str, int]
    field: str
    expected: Any
    actual: Any
    message: str

    def to_dict(self) -> dict[str, Any]:
        data = asdict(self)
        data["ids"] = dict(self.ids)
        data["expected"] = _jsonable(self.expected)
        data["actual"] = _jsonable(self.actual)
        return data


@dataclass
class CheckResult:
    name: str
    description: str
    severity: Severity
    rows_checked: int
    findings: list[Finding] = field(default_factory=list)

    @property
    def passed(self) -> bool:
        return not self.findings

    def to_dict(self, max_findings: int | None = None) -> dict[str, Any]:
        findings = self.findings if max_findings is None else self.findings[:max_findings]
        return {
            "name": self.name,
            "description": self.description,
            "severity": str(self.severity),
            "passed": self.passed,
            "rows_checked": self.rows_checked,
            "finding_count": len(self.findings),
            "findings": [finding.to_dict() for finding in findings],
        }


def _jsonable(value: Any) -> Any:
    if isinstance(value, Decimal):
        return str(value)
    if hasattr(value, "item"):
        return value.item()
    return value

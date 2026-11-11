"""Account-level anomaly detection: a MAD baseline and an IsolationForest, evaluated against known labels."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import pandas as pd

from trading_analytics.anomaly import baseline, evaluate, features, forest
from trading_analytics.config import DomainSettings
from trading_analytics.extract import Dataset


@dataclass
class AnomalyReport:
    features: pd.DataFrame
    baseline: baseline.BaselineResult
    forest: forest.ForestResult
    labels: pd.Series | None
    evaluation: dict[str, dict[str, Any]]

    def table(self) -> pd.DataFrame:
        """One row per account: features, both verdicts, the baseline's strongest feature, and the label."""
        table = self.features.copy()
        table["baseline_score"] = self.baseline.scores.abs().max(axis=1)
        table["baseline_flag"] = self.baseline.flagged
        table["isolation_score"] = self.forest.scores
        table["isolation_flag"] = self.forest.flagged
        table["top_feature"] = [
            self.baseline.top_features(account_id, 1)[0][0] if len(self.features.columns) else ""
            for account_id in table.index
        ]
        table["label"] = self.labels.reindex(table.index) if self.labels is not None else None
        return table.reset_index()

    def to_dict(self) -> dict[str, Any]:
        table = self.table()
        flagged = table[table["baseline_flag"] | table["isolation_flag"]]
        return {
            "accounts_scored": len(table),
            "feature_columns": list(self.features.columns),
            "baseline": {"threshold": self.baseline.threshold, "flagged": int(self.baseline.flagged.sum())},
            "isolation_forest": {
                "contamination": self.forest.contamination,
                "flagged": int(self.forest.flagged.sum()),
            },
            "evaluation": self.evaluation,
            "flagged_accounts": [
                {
                    "account_id": int(row.account_id),
                    "baseline_flag": bool(row.baseline_flag),
                    "isolation_flag": bool(row.isolation_flag),
                    "baseline_score": float(row.baseline_score),
                    "isolation_score": float(row.isolation_score),
                    "top_features": self.baseline.top_features(int(row.account_id)),
                    "label": None if pd.isna(row.label) else str(row.label),
                }
                for row in flagged.itertuples(index=False)
            ],
        }


def analyse(dataset: Dataset, settings: DomainSettings, labels_path: Path | None = None) -> AnomalyReport:
    table = features.build(dataset, settings)
    baseline_result = baseline.detect(table)
    forest_result = forest.detect(table)
    labels = evaluate.load_labels(labels_path) if labels_path else None
    evaluation = {}
    if labels is not None and len(table):
        evaluation = {
            "baseline": evaluate.evaluate(baseline_result.flagged, labels),
            "isolation_forest": evaluate.evaluate(forest_result.flagged, labels),
        }
    return AnomalyReport(table, baseline_result, forest_result, labels, evaluation)

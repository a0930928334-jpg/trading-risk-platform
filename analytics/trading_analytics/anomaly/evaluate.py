"""Scores the detectors against the labels the load generator wrote."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pandas as pd
from sklearn.metrics import confusion_matrix, f1_score, precision_score, recall_score

NORMAL_LABEL = "normal"


def load_labels(path: Path) -> pd.Series:
    """Maps account id to its scripted label; accounts the load generator did not create are absent."""
    data = json.loads(path.read_text(encoding="utf-8"))
    labels = {int(account["account_id"]): str(account["label"]) for account in data["accounts"]}
    return pd.Series(labels, name="label")


def evaluate(flagged: pd.Series, labels: pd.Series) -> dict[str, Any]:
    """Precision, recall and F1 of a detector, plus recall per scripted anomaly type.

    Only labelled accounts count: an account that was not created by the load generator has no ground
    truth and is left out rather than assumed normal.
    """
    labelled = labels.reindex(flagged.index).dropna()
    predicted = flagged.reindex(labelled.index).fillna(False).astype(bool).to_numpy()
    actual = (labelled != NORMAL_LABEL).to_numpy()
    matrix = confusion_matrix(actual, predicted, labels=[False, True])
    result: dict[str, Any] = {
        "labelled_accounts": len(labelled),
        "anomalous_accounts": int(actual.sum()),
        "flagged_accounts": int(predicted.sum()),
        "precision": float(precision_score(actual, predicted, zero_division=0)),
        "recall": float(recall_score(actual, predicted, zero_division=0)),
        "f1": float(f1_score(actual, predicted, zero_division=0)),
        "true_positives": int(matrix[1, 1]),
        "false_positives": int(matrix[0, 1]),
        "false_negatives": int(matrix[1, 0]),
        "true_negatives": int(matrix[0, 0]),
        "recall_by_type": {},
    }
    for label in sorted(set(labelled) - {NORMAL_LABEL}):
        of_type = labelled == label
        caught = int(predicted[of_type.to_numpy()].sum())
        result["recall_by_type"][label] = {"accounts": int(of_type.sum()), "caught": caught}
    return result

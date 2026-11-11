"""Detectors and evaluation on constructed feature tables (no database needed)."""

from __future__ import annotations

import json

import numpy as np
import pandas as pd
import pytest

from trading_analytics.anomaly import baseline, evaluate, forest
from trading_analytics.anomaly.features import FEATURE_COLUMNS


def _features(seed: int = 7, normal: int = 40) -> pd.DataFrame:
    """40 similar accounts plus three scripted outliers, one per feature group."""
    rng = np.random.default_rng(seed)
    table = pd.DataFrame(
        {
            "orders": rng.integers(10, 20, normal),
            "reject_rate": rng.uniform(0.0, 0.1, normal),
            "user_cancel_rate": rng.uniform(0.0, 0.2, normal),
            "orders_per_trade": rng.uniform(1.0, 2.0, normal),
            "max_notional_ratio": rng.uniform(0.01, 0.05, normal),
            "mean_notional_ratio": rng.uniform(0.005, 0.03, normal),
            "orders_per_active_minute": rng.uniform(0.5, 2.0, normal),
            "median_seconds_to_cancel": rng.uniform(20, 60, normal),
        },
        index=pd.RangeIndex(1, normal + 1, name="account_id"),
    )
    outliers = pd.DataFrame(
        {
            "orders": [60, 40, 10],
            "reject_rate": [0.0, 0.95, 0.0],
            "user_cancel_rate": [0.9, 0.0, 0.0],
            "orders_per_trade": [60.0, 40.0, 10.0],
            "max_notional_ratio": [0.02, 0.9, 0.99],
            "mean_notional_ratio": [0.01, 0.4, 0.95],
            "orders_per_active_minute": [20.0, 5.0, 1.0],
            "median_seconds_to_cancel": [0.2, 40.0, 40.0],
        },
        index=pd.Index([101, 102, 103], name="account_id"),
    )
    return pd.concat([table, outliers])[FEATURE_COLUMNS].astype(float)


def test_baseline_flags_the_outliers_and_names_the_feature():
    result = baseline.detect(_features())
    assert set(result.flagged[result.flagged].index) == {101, 102, 103}
    assert result.top_features(101, 1)[0][0] in {
        "user_cancel_rate",
        "orders_per_trade",
        "orders_per_active_minute",
    }
    assert result.top_features(102, 1)[0][0] in {"reject_rate", "orders_per_trade", "max_notional_ratio"}
    assert result.top_features(103, 1)[0][0] in {"max_notional_ratio", "mean_notional_ratio"}


def test_baseline_handles_constant_features_without_dividing_by_zero():
    table = _features()
    table["reject_rate"] = 0.0
    table.loc[102, "reject_rate"] = 0.9
    scores = baseline.robust_z_scores(table)
    assert np.isfinite(scores.to_numpy()).all()
    assert scores.loc[102, "reject_rate"] > baseline.DEFAULT_THRESHOLD


def test_isolation_forest_is_deterministic_and_finds_the_outliers():
    table = _features()
    first = forest.detect(table)
    second = forest.detect(table)
    pd.testing.assert_series_equal(first.scores, second.scores)
    flagged = set(first.flagged[first.flagged].index)
    assert {101, 102, 103} <= flagged
    # contamination 0.1 of 43 accounts rounds to at most 4-5 flags
    assert len(flagged) <= 5


def test_evaluation_scores_only_labelled_accounts(tmp_path):
    labels_file = tmp_path / "labels.json"
    labels_file.write_text(
        json.dumps(
            {
                "accounts": [
                    {"account_id": 1, "label": "normal"},
                    {"account_id": 2, "label": "normal"},
                    {"account_id": 3, "label": "normal"},
                    {"account_id": 101, "label": "cancel_burst"},
                    {"account_id": 102, "label": "risk_repeater"},
                ]
            }
        )
    )
    labels = evaluate.load_labels(labels_file)
    flagged = pd.Series({1: False, 2: True, 3: False, 101: True, 102: False, 999: True})

    scores = evaluate.evaluate(flagged, labels)

    assert scores["labelled_accounts"] == 5
    assert scores["anomalous_accounts"] == 2
    assert scores["flagged_accounts"] == 2  # account 999 is unlabelled and ignored
    assert scores["true_positives"] == 1
    assert scores["false_positives"] == 1
    assert scores["false_negatives"] == 1
    assert scores["precision"] == pytest.approx(0.5)
    assert scores["recall"] == pytest.approx(0.5)
    assert scores["f1"] == pytest.approx(0.5)
    assert scores["recall_by_type"] == {
        "cancel_burst": {"accounts": 1, "caught": 1},
        "risk_repeater": {"accounts": 1, "caught": 0},
    }

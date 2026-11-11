"""IsolationForest detector from scikit-learn."""

from __future__ import annotations

from dataclasses import dataclass

import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import RobustScaler

RANDOM_STATE = 42
# Expected share of anomalous accounts. Fixed in advance rather than derived from the labels, because
# in production the labels are unknown.
CONTAMINATION = 0.1


@dataclass
class ForestResult:
    scores: pd.Series
    flagged: pd.Series
    contamination: float


def detect(features: pd.DataFrame, contamination: float = CONTAMINATION) -> ForestResult:
    if len(features) < 2:
        empty = pd.Series(dtype=float, index=features.index)
        return ForestResult(scores=empty, flagged=empty.astype(bool), contamination=contamination)
    scaled = RobustScaler().fit_transform(features.to_numpy())
    model = IsolationForest(n_estimators=200, contamination=contamination, random_state=RANDOM_STATE)
    predictions = model.fit_predict(scaled)
    # score_samples is higher for normal points; negating gives "higher is more anomalous".
    scores = pd.Series(-model.score_samples(scaled), index=features.index, name="isolation_score")
    flagged = pd.Series(predictions == -1, index=features.index, name="isolation_forest")
    return ForestResult(scores=scores, flagged=flagged, contamination=contamination)

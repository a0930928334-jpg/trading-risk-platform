"""Explainable baseline: robust z-scores from the median and the median absolute deviation (MAD).

An account is flagged when any feature is further than `threshold` robust standard deviations from
the median. 3.5 is the cut-off recommended by Iglewicz and Hoaglin for the modified z-score.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd

# Scales the MAD to the standard deviation of a normal distribution.
MAD_TO_SIGMA = 1.4826
DEFAULT_THRESHOLD = 3.5


@dataclass
class BaselineResult:
    scores: pd.DataFrame
    flagged: pd.Series
    threshold: float

    def top_features(self, account_id: int, count: int = 3) -> list[tuple[str, float]]:
        row = self.scores.loc[account_id].abs().sort_values(ascending=False)
        return [(str(name), float(value)) for name, value in row.head(count).items()]


def robust_z_scores(features: pd.DataFrame) -> pd.DataFrame:
    median = features.median()
    mad = (features - median).abs().median() * MAD_TO_SIGMA
    # A feature that is constant for more than half the accounts has MAD 0. Falling back to the mean
    # absolute deviation keeps the feature informative instead of dividing by zero.
    fallback = (features - median).abs().mean() * 1.2533
    scale = mad.where(mad > 0, fallback)
    z = (features - median) / scale
    return z.replace([np.inf, -np.inf], np.nan).fillna(0.0)


def detect(features: pd.DataFrame, threshold: float = DEFAULT_THRESHOLD) -> BaselineResult:
    scores = robust_z_scores(features)
    flagged = (scores.abs() >= threshold).any(axis=1)
    return BaselineResult(scores=scores, flagged=flagged, threshold=threshold)

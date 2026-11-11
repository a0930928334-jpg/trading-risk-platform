"""Seeded load generator: normal accounts plus a few scripted anomalies, driven through the REST API.

It writes a labels file so the anomaly detectors can be scored against the ground truth. It never
touches the database.
"""

from __future__ import annotations

import json
import logging
import random
import time
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from trading_analytics.loadgen import scenarios
from trading_analytics.loadgen.client import Session, wait_for_health

log = logging.getLogger(__name__)

PASSWORD = "Password123!"


@dataclass(frozen=True)
class LoadProfile:
    seed: int
    normal: int = 40
    cancel_burst: int = 2
    risk_repeater: int = 2
    notional_prober: int = 2

    def labels(self) -> list[str]:
        return (
            [scenarios.NORMAL] * self.normal
            + [scenarios.CANCEL_BURST] * self.cancel_burst
            + [scenarios.RISK_REPEATER] * self.risk_repeater
            + [scenarios.NOTIONAL_PROBER] * self.notional_prober
        )


def run(base_url: str, profile: LoadProfile, labels_out: Path) -> dict[str, Any]:
    wait_for_health(base_url, attempts=60, delay_seconds=2.0)
    rng = random.Random(profile.seed)
    # The seed drives every decision; the timestamp only keeps e-mail addresses unique across runs
    # against the same database.
    run_id = f"{profile.seed}-{int(time.time())}"

    # Every endpoint except registration and login needs a token, so market data is read through an
    # observer account that never places an order and therefore never appears in the scored features.
    market_session = Session(base_url)
    market_session.register_and_login(f"loadgen-{run_id}-observer@example.com", PASSWORD, "Load observer")
    market = scenarios.Market(symbols=[instrument["symbol"] for instrument in market_session.instruments()])
    market.refresh(market_session)
    scenarios.set_market(market)

    actors: list[scenarios.Actor] = []
    labels = profile.labels()
    rng.shuffle(labels)
    for index, label in enumerate(labels):
        session = Session(base_url)
        session.register_and_login(
            f"loadgen-{run_id}-{index:03d}@example.com", PASSWORD, f"Load {label} {index}"
        )
        actors.append(scenarios.Actor(label, session, random.Random(rng.randrange(2**31))))
    log.info(
        "Registered %d accounts (%s)", len(actors), ", ".join(f"{k}={v}" for k, v in _counts(labels).items())
    )

    # Round-robin ticks interleave the accounts, so anomalies are spread through the timeline rather than
    # clustered at the end.
    active = list(actors)
    tick = 0
    while active:
        rng.shuffle(active)
        active = [actor for actor in active if actor.step()]
        tick += 1
        if tick % 5 == 0:
            market.refresh(market_session)
    totals = {key: sum(actor.stats[key] for actor in actors) for key in ("placed", "rejected", "cancelled")}
    log.info("Done after %d ticks: %s", tick, totals)

    labels_out.parent.mkdir(parents=True, exist_ok=True)
    document = {
        "generated_at": datetime.now(UTC).isoformat(),
        "base_url": base_url,
        "seed": profile.seed,
        "run_id": run_id,
        "ticks": tick,
        "totals": totals,
        "accounts": [
            {"account_id": actor.session.account_id, "label": actor.label, **actor.stats} for actor in actors
        ],
    }
    labels_out.write_text(json.dumps(document, indent=2), encoding="utf-8")
    return document


def _counts(labels: list[str]) -> dict[str, int]:
    return {label: labels.count(label) for label in scenarios.LABELS if label in labels}

"""Scripted account behaviours.

Each scenario is a generator of steps so that accounts can be interleaved in time: the runner advances
every account one step per tick instead of running accounts one after another. Prices are kept inside
the 10 percent price band around the mark price unless the scenario means to break a rule.
"""

from __future__ import annotations

import random
from collections.abc import Iterator
from dataclasses import dataclass, field
from typing import Any

from trading_analytics.loadgen.client import ApiError, Session

NORMAL = "normal"
CANCEL_BURST = "cancel_burst"
RISK_REPEATER = "risk_repeater"
NOTIONAL_PROBER = "notional_prober"

LABELS = (NORMAL, CANCEL_BURST, RISK_REPEATER, NOTIONAL_PROBER)

# Mirrors app.risk.max-order-notional and app.risk.price-band-percent in application.yml.
MAX_ORDER_NOTIONAL = 100_000.0
PRICE_BAND_PERCENT = 10.0


@dataclass
class Market:
    """Reference data shared by every account; refreshed from the API by the runner."""

    symbols: list[str]
    mark: dict[str, float] = field(default_factory=dict)
    best_bid: dict[str, float | None] = field(default_factory=dict)
    best_ask: dict[str, float | None] = field(default_factory=dict)

    def refresh(self, session: Session) -> None:
        for instrument in session.instruments():
            symbol = instrument["symbol"]
            self.mark[symbol] = float(instrument["lastTradePrice"] or instrument["referencePrice"])
            book = session.book(symbol, depth=1)
            self.best_bid[symbol] = float(book["bids"][0]["price"]) if book["bids"] else None
            self.best_ask[symbol] = float(book["asks"][0]["price"]) if book["asks"] else None


@dataclass
class Actor:
    label: str
    session: Session
    rng: random.Random
    steps: Iterator[None] = field(init=False)
    stats: dict[str, int] = field(default_factory=lambda: {"placed": 0, "rejected": 0, "cancelled": 0})

    def __post_init__(self) -> None:
        self.steps = SCENARIOS[self.label](self)

    def step(self) -> bool:
        """Runs one step; returns False when the scenario has finished."""
        try:
            next(self.steps)
        except StopIteration:
            return False
        return True

    def place(self, symbol: str, side: str, price: float, quantity: int) -> dict[str, Any]:
        order = self.session.place(symbol, side, round(price, 2), quantity)
        self.stats["placed"] += 1
        if order["status"] == "REJECTED":
            self.stats["rejected"] += 1
        return order

    def cancel(self, order_id: int) -> None:
        if self.session.cancel(order_id) is not None:
            self.stats["cancelled"] += 1


_market: Market | None = None


def set_market(market: Market) -> None:
    global _market
    _market = market


def market() -> Market:
    assert _market is not None, "set_market() must run before the scenarios"
    return _market


def _passive_price(symbol: str, side: str, rng: random.Random) -> float:
    """A limit price that rests: below the best bid for buys, above the best ask for sells."""
    mark = market().mark[symbol]
    best = market().best_bid[symbol] if side == "BUY" else market().best_ask[symbol]
    anchor = best if best is not None else mark
    offset = rng.uniform(0.001, 0.02) * anchor
    return anchor - offset if side == "BUY" else anchor + offset


def _marketable_price(symbol: str, side: str) -> float:
    """A limit price at the opposite best price, so the order trades immediately if liquidity exists."""
    mark = market().mark[symbol]
    best = market().best_ask[symbol] if side == "BUY" else market().best_bid[symbol]
    return best if best is not None else mark


def normal(actor: Actor) -> Iterator[None]:
    """Deposits, then alternates between resting limit orders, marketable orders and occasional cancels."""
    rng = actor.rng
    actor.session.deposit(round(rng.uniform(50_000, 150_000), 2))
    yield
    for _ in range(rng.randint(12, 20)):
        symbol = rng.choice(market().symbols)
        roll = rng.random()
        if roll < 0.15:
            open_orders = actor.session.open_orders()
            if open_orders:
                actor.cancel(int(rng.choice(open_orders)["id"]))
            yield
            continue
        held = actor.session.positions()
        side = "SELL" if held.get(symbol, 0) > 0 and rng.random() < 0.45 else "BUY"
        quantity = rng.randint(1, 20) if side == "BUY" else rng.randint(1, max(1, min(20, held[symbol])))
        price = _marketable_price(symbol, side) if roll < 0.55 else _passive_price(symbol, side, rng)
        if side == "BUY" and price * quantity > actor.session.available_cash():
            quantity = max(1, int(actor.session.available_cash() // price))
        _place_quietly(actor, symbol, side, price, quantity)
        yield


def cancel_burst(actor: Actor) -> Iterator[None]:
    """Floods one instrument with resting orders and cancels nearly all of them straight away."""
    rng = actor.rng
    actor.session.deposit(100_000)
    yield
    symbol = rng.choice(market().symbols)
    for _ in range(rng.randint(45, 60)):
        order = _place_quietly(actor, symbol, "BUY", _passive_price(symbol, "BUY", rng), rng.randint(1, 5))
        if order is not None and order["status"] == "NEW" and rng.random() < 0.9:
            actor.cancel(int(order["id"]))
        yield


def risk_repeater(actor: Actor) -> Iterator[None]:
    """Keeps sending orders that a risk rule rejects: out of band, over the value cap, or unaffordable."""
    rng = actor.rng
    actor.session.deposit(5_000)
    yield
    for _ in range(rng.randint(30, 45)):
        symbol = rng.choice(market().symbols)
        mark = market().mark[symbol]
        kind = rng.choice(("band", "notional", "funds"))
        if kind == "band":
            factor = 1 + (PRICE_BAND_PERCENT + rng.uniform(5, 25)) / 100
            price, quantity = mark * (factor if rng.random() < 0.5 else 1 / factor), rng.randint(1, 10)
        elif kind == "notional":
            price, quantity = mark, int(MAX_ORDER_NOTIONAL / mark) + rng.randint(5, 200)
        else:
            price, quantity = mark * 1.01, int(6_000 / mark) + rng.randint(1, 50)
        _place_quietly(actor, symbol, "BUY", price, quantity)
        yield


def notional_prober(actor: Actor) -> Iterator[None]:
    """Places orders just under the maximum order value, repeatedly, far from the market."""
    rng = actor.rng
    actor.session.deposit(1_000_000)
    yield
    for _ in range(rng.randint(8, 12)):
        symbol = rng.choice(market().symbols)
        price = market().mark[symbol] * (1 - rng.uniform(0.06, 0.09))
        quantity = int(MAX_ORDER_NOTIONAL * rng.uniform(0.93, 0.995) / price)
        if price * quantity > actor.session.available_cash():
            open_orders = actor.session.open_orders()
            for order in open_orders:
                actor.cancel(int(order["id"]))
        _place_quietly(actor, symbol, "BUY", price, quantity)
        yield


def _place_quietly(
    actor: Actor, symbol: str, side: str, price: float, quantity: int
) -> dict[str, Any] | None:
    try:
        return actor.place(symbol, side, price, quantity)
    except ApiError as error:
        # 400 validation errors (for example quantity 0) are the scenario's fault, not the system's.
        if error.status in (400, 503):
            return None
        raise


SCENARIOS = {
    NORMAL: normal,
    CANCEL_BURST: cancel_burst,
    RISK_REPEATER: risk_repeater,
    NOTIONAL_PROBER: notional_prober,
}

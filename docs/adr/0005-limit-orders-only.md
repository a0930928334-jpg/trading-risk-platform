# 5. Limit orders only (GTC) in v0.1

- Status: Accepted
- Date: 2026-10-17

## Context

The reservation model (ADR 0002) reserves the full worst-case cost of a buy order when it is placed.
That needs a price.

## Options

1. **Support market orders** and reserve against an estimate (for example the best ask plus a buffer).
   The estimate can be wrong when the book is thin or moves, so the fill may cost more than was reserved.
2. **Support market orders by walking the book at placement** to compute the exact cost. Correct only
   while nothing else trades, which ties validation to matching and complicates the risk chain.
3. **Limit orders only.** The limit price is the worst price the user accepts, so `quantity x limit`
   is an exact upper bound on the cost.

## Decision

Option 3. `OrderType` has only `LIMIT` and `TimeInForce` only `GTC` (good till cancelled); both are
optional in the request and default to these values. A request with `"type": "MARKET"` is rejected
with 400. Quantities are whole shares (`long`).

## Consequences

- Reservations are exact, and every risk check works on a known price.
- Users who want to trade immediately can send a marketable limit order (a limit at or through the best
  opposite price); price improvement is released automatically.
- Market orders, IOC/FOK and day orders are on the roadmap.

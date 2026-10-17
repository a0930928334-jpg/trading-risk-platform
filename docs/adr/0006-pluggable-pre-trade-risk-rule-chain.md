# 6. Pluggable pre-trade risk rule chain

- Status: Accepted
- Date: 2026-10-17

## Context

Every order must pass several independent pre-trade checks before it can reserve funds or trade.
More checks will be added later, and each one must be testable on its own.

## Options

1. **One validation method** with a sequence of `if` statements. Quick to write, but it grows without limit,
   rules cannot be tested in isolation, and adding a rule means editing shared code.
2. **Bean Validation constraints** on the request. Good for shape and range checks, but rules need locked
   account state, which is not available during request binding.
3. **A chain of `RiskRule` beans** evaluated in a defined order against a `RiskContext`.

## Decision

Option 3.

- `RiskRule` has a single method: `RiskDecision evaluate(RiskContext context)`.
- Rules are Spring components ordered with `@Order`; `RiskEngine` receives them as a list and stops at the
  first rejection.
- `RiskContext` carries the order, account, instrument and the **already locked** cash and position rows,
  so the checked values cannot change before the reservation is written.
- v0.1 rules, in order: `AccountStatusRule`, `InstrumentStatusRule`, `MaxOrderNotionalRule`
  (`app.risk.max-order-notional`, default 100,000), `PriceBandRule` (`app.risk.price-band-percent`,
  default 10% from the last trade price, or the reference price before the first trade),
  `AvailableCashRule`, `AvailablePositionRule`.
- A rejected order is **stored** with status `REJECTED`, a `rejectCode` and a `rejectReason`, and the API
  returns 201 with that order. Requests that fail validation (400) or name an unknown symbol (404) are not stored.

## Consequences

- Adding a rule means adding one class; no existing code changes.
- Each rule has its own unit test with no Spring context or database.
- Rejected orders give an audit trail and data for later monitoring.
- Rules see one order at a time; checks that need history (order rate limits, daily loss limits) need
  extra state and are on the roadmap.

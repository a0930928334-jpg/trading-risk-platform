# 2. Cash reservation and an append-only cash ledger

- Status: Accepted
- Date: 2026-10-17

## Context

A buy order commits cash before it trades. It may rest for a long time, fill in parts at different prices,
or be cancelled. The system must stop a user from committing the same cash twice, and must be able to
explain every change to a balance.

## Options

1. **Debit cash when the order is placed** and refund on cancel. Partial fills and price improvement turn
   into refund arithmetic; the balance no longer says what the user owns.
2. **Check the balance only at match time.** Users can place more orders than they can pay for; a later
   fill can fail after earlier ones have succeeded.
3. **Reserve at placement** (`balance`, `reserved`, `available = balance - reserved`) and record every
   movement in a ledger.

## Decision

Option 3.

- `cash_balances.reserved` with database checks `reserved >= 0` and `reserved <= balance`.
- `cash_ledger_entries` is insert-only. Each entry has a positive `amount`, a type
  (`DEPOSIT`, `RESERVE`, `RELEASE`, `TRADE_DEBIT`, `TRADE_CREDIT`), the `balance_after` and
  `reserved_after` snapshots, and the related order or trade.
- `CashService` is the only class that changes cash; every method writes the matching ledger entry and
  requires an existing transaction (`Propagation.MANDATORY`).
- A buy order reserves `quantity x limit price`. A fill at a better price releases the difference straight
  away, so an open buy order always holds exactly `remaining quantity x limit price`.
- Amounts are `BigDecimal` with scale 4; USD is the only currency.
- Shares use the same model: `positions.quantity` and `reserved_quantity`. Trades are the record of share
  movements, so there is no separate share ledger.

## Consequences

- The ledger replays to the current balance and reservation; tests check this, including after
  concurrent trading.
- Cancelling or filling never needs a stored "amount reserved" per order: it is derived from the order.
- Every placement and fill writes several rows, which is more I/O than a balance-only design.
- No multi-currency support; adding it would need a currency dimension on balances and ledger entries.

# 4. Locking strategy: per-instrument locks and ordered pessimistic row locks

- Status: Accepted
- Date: 2026-10-17

## Context

Two kinds of shared state change concurrently:

- the in-memory order book of each instrument, which is not thread-safe;
- cash and position rows. One trade touches the buyer's and the seller's rows, and the market maker's
  cash row is involved in most trades on every instrument.

Lost updates, an inconsistent book, and deadlocks must all be impossible.

## Options

1. **Optimistic locking** (`@Version`) with retries. No blocking, but hot rows conflict constantly under
   load, and retries add latency and starvation risk.
2. **A single global lock.** Simple and correct, but serialises every instrument.
3. **One lock per instrument, plus `PESSIMISTIC_WRITE` row locks taken in a fixed global order.**

## Decision

Option 3.

- `InstrumentLockManager` holds a fair `ReentrantLock` per instrument. Place, cancel and book reads take
  it, so each book is changed by one thread at a time; different instruments run in parallel. Acquiring
  times out after 10 seconds with a 503 (`instrument-busy`). The lock is taken **outside** the transaction
  and released after the commit and the book update.
- Inside the transaction, rows are locked with `SELECT ... FOR UPDATE`, always in this order:
  1. all involved **cash** rows, by ascending account ID;
  2. then all involved **position** rows, by ascending account ID.
- The matching engine runs first, without changing the book, so the full set of counterparties is known
  before any row is locked.
- A position row that does not exist yet is created with `INSERT ... ON CONFLICT DO NOTHING` and then locked.

## Consequences

- No deadlock between transactions: transactions on the same instrument never overlap; transactions on
  different instruments can only share cash rows, and both lock those in the same order.
- Cross-instrument throughput is limited by contention on popular cash rows (for example the market maker).
- The instrument lock lives in one JVM; this is the main reason v0.1 is a single instance (ADR 0003).
- `ConcurrentTradingConservationTest` runs 12 threads over shared accounts and two instruments and checks
  that cash and shares are conserved, nothing goes negative, reservations match open orders and the books
  match the database.

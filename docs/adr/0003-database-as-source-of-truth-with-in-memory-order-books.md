# 3. Database as the source of truth, in-memory order books as a cache

- Status: Accepted
- Date: 2026-10-17

## Context

Matching needs the best resting prices quickly and in price-time order. Orders, trades, cash and positions
must never be lost or disagree with each other. v0.1 runs as a single instance.

## Options

1. **Match in SQL** by querying resting orders on each request. Always consistent, but every order pays for
   a sorted query and row locks on the opposite side of the book.
2. **In-memory book as the source of truth**, with asynchronous persistence or event sourcing. Fast, but needs
   a durable log and replay to survive a crash, which is out of scope.
3. **In-memory book as a cache of the open orders in PostgreSQL**, changed only after the database commits.

## Decision

Option 3.

1. Take the instrument lock (ADR 0004).
2. In one transaction: run the matching engine against the book **without changing it**, lock the involved
   rows, run the risk checks, then persist the order, trades, cash and positions.
3. Only **after the commit**, apply the same match result to the book.
4. If the transaction fails unexpectedly, rebuild that instrument's book from the `orders` table.
   Before settling a fill, the maker order is re-read; if it is no longer open the book is stale, the
   transaction rolls back and the book is rebuilt.
5. At startup (`SmartInitializingSingleton`, before the web server accepts requests) rebuild every book that
   has open orders, inserting them in ascending order ID so time priority is preserved.

The matching core (`matching` package) is plain Java with no Spring or database dependency, which keeps it
unit-testable on its own.

## Consequences

- A crash or failed transaction cannot leave a fill in memory that is not in the database.
- Order book reads are in memory; only the rows involved in a trade are written.
- Order books live in one JVM, so the service cannot run as several instances serving the same instrument.
  Scaling out means partitioning instruments across instances (each instance owning a set of symbols).
- Rebuild cost grows with the number of open orders; it is a single indexed query per instrument
  (`idx_orders_open_by_instrument`).

# 8. Read-only database access for analytics, with a rule baseline next to IsolationForest

- Status: Accepted
- Date: 2026-11-11

## Context

The roadmap item "Python anomaly detection on order and trade flow" needs the order, trade, cash and
position history. It also needs to say whether that history is internally consistent: a detector that
runs on corrupt data reports noise. The Java application exposes a REST API scoped to one user per token
and has no cross-account read model, and the analytics code is written in Python so it can use pandas
and scikit-learn.

## Options

### Where the data comes from

1. **Extend the REST API** with admin endpoints that return every account's orders, trades and ledger.
   Keeps one access path, but adds an admin role, pagination of large tables and a second copy of each
   query to the Java code, for a consumer that only reads.
2. **Publish events** (Kafka is on the roadmap) and build the analytics on the stream. Needs the event
   pipeline first, and a stream cannot answer "does the ledger replay to the balance right now".
3. **Read the PostgreSQL tables directly**, read-only, in Python.

### How anomalies are found

1. **Rules only**: thresholds on cancel rate, reject rate and order size.
2. **A learned detector only**: IsolationForest on per-account features.
3. **Both**, scored against the same labelled data.

## Decision

Data: option 3. The analytics module connects to PostgreSQL with its own read-only transactions
(`postgresql_readonly=True`, so the server rejects any write) and reads every table in one
`REPEATABLE READ` snapshot. SQL lives in `.sql` files; the module never writes to a business table. The
database is the source of truth (ADR 0003), so the checks test the truth rather than a projection of it,
and SQL over the tables is the natural language for the invariants (ledger replay, reservations against
open orders, conservation). The load generator, which does create data, goes through the REST API like
any other client.

Detection: option 3. The baseline is a robust z-score per feature (median and MAD, threshold 3.5). The
second detector is scikit-learn's IsolationForest with a fixed seed and a fixed contamination. Both run
on the same per-account features and, when the load generator's labels are available, both are scored
with precision, recall and F1. The report shows the two results side by side and does not pick a winner
in code.

## Consequences

- The analytics module depends on the table layout. A Flyway migration that renames a column breaks a
  `.sql` file; the pytest suite builds its schema from the same migrations, so CI catches this.
- Reads are not isolated from the application's writes by anything but the snapshot. Large tables would
  need incremental extraction; at the current scale a full read is fine.
- A read-only transaction is a server-side guarantee, so a bug in the Python code cannot change a
  balance. It also rules out materialised views or result tables in the application database; results
  go to files.
- The baseline is explainable: every flag names the feature and its distance from the median, which is
  what an analyst needs to act. IsolationForest can find combinations no single threshold describes, at
  the cost of a score that needs interpretation. Keeping both makes the comparison part of the report, so
  the choice between them is made on evidence as the data changes rather than once in an ADR.
- The evaluation is only as good as the labels: today they come from scripted anomalies in synthetic
  load, so the scores describe those scripts, not real behaviour.

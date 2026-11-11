# Analytics

A Python module that reads the trading platform's PostgreSQL database and answers three questions:
does the data still satisfy the invariants the Java application promises, how is the order flow
behaving, and which accounts behave unlike the others. It never writes to the database.

## Running it

Prerequisites: Python 3.12 and a PostgreSQL that the application has written to. The defaults match
`docker-compose.yml` in the repository root.

```bash
cd analytics
python -m venv .venv
.venv/bin/pip install -r requirements.txt     # .venv\Scripts\pip on Windows
.venv/bin/pip install -e . --no-deps
```

Checks, metrics, anomaly detection and the report in one run:

```bash
python -m trading_analytics run --out report/
```

This writes `report/report.html` (self-contained: inline styles and charts) and `report/summary.json`,
prints each check's verdict, and exits with code 1 if any check failed (0 otherwise, 2 on an error such
as an unreachable database). `python -m trading_analytics check` runs only the checks.

To score the anomaly detectors against known labels, first drive the application with the load
generator, then pass its labels file to `run`:

```bash
# the application must be running with the dev profile (market maker liquidity)
python -m trading_analytics loadgen --seed 20261111 --labels-out report/labels.json
python -m trading_analytics run --out report/ --labels report/labels.json
```

Connection settings come from environment variables:

| Variable | Default | Meaning |
|---|---|---|
| `TRADING_DB_HOST`, `TRADING_DB_PORT` | `localhost`, `5432` | PostgreSQL address |
| `TRADING_DB_NAME`, `TRADING_DB_USER`, `TRADING_DB_PASSWORD` | `trading_platform`, `trading_user`, `trading_password` | Database and credentials |
| `TRADING_MAX_ORDER_NOTIONAL` | `100000` | `app.risk.max-order-notional`; used to scale order values |
| `TRADING_MARKET_MAKER_SHARES_PER_INSTRUMENT` | `100000` | `app.market-maker.shares-per-instrument`; the shares granted to each system account, which the database does not record |
| `TRADING_TEST_DB_NAME` | `trading_platform_analytics_test` | Database the tests create on the same server |

## Read-only by construction

Every connection is opened with `postgresql_readonly=True`, so PostgreSQL runs each transaction as
`READ ONLY` and refuses any `INSERT`, `UPDATE` or `DELETE` (`tests/test_read_only.py` proves it). All
tables are read inside one `REPEATABLE READ` transaction, so the checks see a single consistent snapshot
even while the application is trading. Queries live in `trading_analytics/sql/*.sql`; no SQL is built
from strings.

## The checks

Each check returns the check name, a severity, the number of rows examined and a list of findings. A
finding names the entity and its identifiers (account, order, trade, ledger entry, position or
instrument), the field, the expected value, the actual value and a sentence explaining the mismatch.
The semantics follow the Java code (`CashService`, `SettlementService`, `OrderCommandService`,
`MatchingEngine`).

| Check | Severity | Invariant |
|---|---|---|
| `ledger_replay` | critical | Replaying each account's `cash_ledger_entries` in id order (DEPOSIT and TRADE_CREDIT add to the balance, RESERVE and RELEASE move the reservation, TRADE_DEBIT lowers both) reproduces every `balance_after` and `reserved_after` snapshot and ends at the row in `cash_balances` |
| `reservations_match_open_orders` | high | Reserved cash equals the sum of remaining quantity x limit price over the account's open buy orders; a position's `reserved_quantity` equals the remaining quantity of its open sell orders |
| `order_fills_match_trades` | high | `filled_quantity` equals the quantity of the order's trades; NEW and REJECTED orders have no fills, PARTIALLY_FILLED some, FILLED all, CANCELLED fewer than all |
| `trades_are_valid` | critical | Trade price lies between the sell and buy limits and equals the resting (maker) order's price; the buy leg is a BUY and the sell leg a SELL; the accounts and instrument on the trade match its orders; buyer and seller differ |
| `cash_and_shares_conserved` | critical | Total balance across accounts equals the sum of all DEPOSIT entries; total shares per instrument equal the grant to system accounts (`TRADING_MARKET_MAKER_SHARES_PER_INSTRUMENT` x number of accounts whose user has status SYSTEM) |
| `positions_match_trades` | critical | Each position equals shares bought minus shares sold in that instrument, plus the grant for system accounts |
| `open_book_not_crossed` | high | For every instrument, the highest open bid is strictly below the lowest open ask |

The grant in the last two checks is the one value the database cannot supply: the market maker seeder
credits shares without a trade and there is no share ledger. If it is wrong for your environment the two
checks fail on every instrument with `expected` equal to the configured grant.

## Metrics

- Overview: orders, accepted and rejected orders, reject rate, fill rate (accepted orders fully
  filled), traded rate (accepted orders with at least one fill), user cancel rate, self-trade
  cancellations, open orders, trades, shares and value traded.
- Rejections per risk rule (`reject_code`): count, share of rejections, rate of all orders.
- Per instrument: orders, reject, fill and cancel rates, open orders, trades, volume.
- Time from placement to first fill, in seconds, separately for takers (filled in the transaction that
  placed them) and makers (rested until crossed): p50, p90, maximum.
- Per account: orders, accepted, rejected, user cancellations, trades, accepted orders per trade.
- Per minute: orders, rejections, user cancellations, trades.

Cancellations are split in two. `CANCELLED` with `reject_code` null is the user's cancel request;
`CANCELLED` with `reject_code = SELF_TRADE_PREVENTED` is the matching engine cancelling the remainder of
an order that would have traded with the same account's resting order.

## Anomaly detection

Features are built per user account (system accounts are excluded): orders placed, reject rate, user
cancel rate, accepted orders per trade, largest and mean order value relative to the risk cap, orders
per active minute, and the median time a user-cancelled order rested.

Two detectors score the same features:

- Baseline: robust z-scores from the median and the median absolute deviation (MAD, scaled by 1.4826).
  An account is flagged when any feature is at least 3.5 robust standard deviations from the median.
  Each flag names the features that caused it, so a reviewer can see why.
- IsolationForest from scikit-learn: 200 trees, contamination 0.1, `random_state=42`, on
  `RobustScaler`-scaled features. Contamination is fixed in advance rather than taken from the labels,
  because in production the labels are unknown.

When the load generator's labels file is supplied, both detectors are scored with precision, recall and
F1 over the labelled accounts, plus recall per scripted anomaly type. Accounts the load generator did
not create have no ground truth and are left out of the evaluation.

Limitations: the data is synthetic and the sample is small (tens of accounts, a handful of anomalies),
so the scores say how the detectors behave on the scripted anomalies, not how they would perform on
real flow.

## Load generator

`python -m trading_analytics loadgen` registers accounts through the REST API and runs them in
interleaved rounds, so normal and anomalous activity overlap in time. With the defaults it creates 40
normal accounts and 2 of each scripted anomaly:

| Label | Behaviour |
|---|---|
| `normal` | Deposits 50,000 to 150,000; places resting and marketable limit orders within 2 percent of the market, sells some of what it bought, cancels occasionally |
| `cancel_burst` | Places 45 to 60 resting bids on one instrument and cancels about 90 percent of them immediately |
| `risk_repeater` | Deposits 5,000, then sends 30 to 45 orders that a risk rule rejects: outside the price band, above the order value cap, or unaffordable |
| `notional_prober` | Deposits 1,000,000 and places 8 to 12 resting bids worth 93 to 99.5 percent of the order value cap |

The generator is seeded (`--seed`) and writes `labels.json` with every account id and label plus per-account
counts of placed, rejected and cancelled orders. It uses only the standard library's `urllib`.

## Reading the report

1. The header shows the generation time, the database, and whether every check passed, with tiles for
   the row counts and the number of accounts flagged by either detector.
2. "Data quality checks" lists all seven checks with their row counts and findings. A failed check is
   followed by a table of up to 25 findings with identifiers, expected and actual values; the JSON summary
   holds up to 200 per check.
3. "Quality metrics" shows the overview tiles, the flow over time, rates by instrument, rejections by
   risk rule, time to first fill, and the accounts with the most orders per trade.
4. "Anomaly detection" shows the evaluation table (when labels were supplied), a scatter of accounts by
   cancel and reject rate coloured by detector verdict, the flagged accounts with their most deviating
   features, and the feature definitions.

`summary.json` has the same content in machine-readable form: `passed`, `row_counts`, `checks` (with
findings), `metrics` and `anomalies` (including the evaluation).

## Tests

```bash
cd analytics
.venv/bin/pytest
```

The tests need a PostgreSQL server. They create `TRADING_TEST_DB_NAME` on it, build the schema from the
repository's Flyway migrations in `src/main/resources/db/migration`, and write a small consistent trading
history (`tests/scenario.py`) that follows the Java bookkeeping rules. Nothing is mocked.

- `test_checks.py`: every check passes on the consistent history; then one row is corrupted per fault
  (a ledger snapshot, a deleted ledger entry, a balance, an open order's price, reserved shares, a
  filled quantity, a trade price, positions, a resting ask) and the test asserts that exactly the
  expected check or checks fail and that every finding points at the corrupted row.
- `test_read_only.py`: an UPDATE through the analytics connection is refused by PostgreSQL.
- `test_metrics.py`, `test_anomaly.py`, `test_cli.py`: hand-computed metric values, detector behaviour
  on constructed features, and the command line end to end including exit codes.

Lint and formatting: `ruff check .` and `ruff format --check .`.

## Continuous integration

The `analytics` job in `.github/workflows/ci.yml` runs ruff and pytest against a PostgreSQL service,
builds the application, starts it with the dev profile, runs the load generator and then the analysis,
and uploads `report/` as a build artifact. A failed check fails the job.

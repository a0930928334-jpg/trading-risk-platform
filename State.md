# Project State

## Current version

**v0.1 — complete.** Every feature listed in the README is implemented, tested and merged to `main`.
Architecture, the full feature list, setup and limitations live in [README.md](README.md); design rationale
lives in [docs/adr](docs/adr).

## Milestones

| Milestone | Scope | Pull request |
|---|---|---|
| M0 Foundation | Login committed, Testcontainers PostgreSQL for tests, GitHub Actions CI, unused Redis/Kafka dependencies removed | #1 |
| M1 Authentication | JWT issuing and validation, protected endpoints, `/api/me`, RFC 9457 errors | #2 |
| M2 Cash | `reserved` column, cash ledger, deposits, account and ledger endpoints | #3 |
| M3 Instruments and positions | Seeded instruments, positions with reservations, dev market maker account | #4 |
| M4 Orders and risk | Order placement and cancellation, pluggable risk rule chain, stored rejections | #5 |
| M5 Matching engine | Pure Java matching core, settlement, order book rebuild, book depth and trades endpoints, self-trade prevention | #6 |
| M6 Polish | Concurrent conservation test, OpenAPI annotations, README, ADRs, demo script | #8 |
| Web dashboard | React + TypeScript front end in `frontend/` with its own CI job | #9 |

## Test status

`./mvnw verify`: 172 test cases, all passing locally and in CI (see the README's Testing section).

## Housekeeping in M6

- `ARCHITECTURE.md`, `API_EXAMPLES.md` and `TECH_STACK.md` were removed: they described the Week 1 skeleton
  and were superseded by the README (architecture, API, tech stack), the ADRs, Swagger UI, `api.http`
  and `scripts/demo.sh`.
- This file replaces the earlier day-by-day log, which is preserved in Git history.

## Next (v0.2 candidates)

See "Limitations and roadmap" in the README: market orders, refresh tokens, Kafka order and trade events,
Redis caching, Python anomaly detection, and partitioning instruments across instances.

# Project State

## Current Week / Day
Week 2 Day 2 completed

## Current Phase
User authentication and account foundation

## Completed
- Created Spring Boot 3 backend project
- Configured Java 21 and Maven
- Added Docker Compose for PostgreSQL, Redis, and Kafka
- Verified Spring Boot application startup
- Verified `/actuator/health` returns UP
- Verified PostgreSQL and Redis health through Spring Boot Actuator
- Added initial Flyway migration file
- Created initial README.md
- Created initial TECH_STACK.md
- Created initial State.md
- Initialized Git repository
- Pushed first commit to GitHub

## In Progress
- Verify Kafka container status
- Complete basic project documentation files
- Prepare project structure for Week 1 Day 2

## Blockers
- Kafka Java integration not implemented yet

## Local Startup Status
- Spring Boot: UP
- PostgreSQL: UP
- Redis: UP
- Kafka: Container running, Java integration pending

## Latest Test Result
- Application startup verified manually through `/actuator/health`
- Flyway initial migration file exists
- No business tests yet

## Next Task
Create clean package structure and add the first basic health check controller.
## Week 1 Day 2 Progress

### Completed

- Added custom system status API:
    - `GET /api/system/status`
- Added response DTO:
    - `SystemStatusResponse`
- Added controller:
    - `SystemStatusController`
- Added first controller test:
    - `SystemStatusControllerTest`
- Verified `/api/system/status` manually in browser.
- Verified `/actuator/health` still returns `UP`.
- Verified PostgreSQL health is `UP`.
- Verified Redis health is `UP`.

### Current Infrastructure Status

- Spring Boot application starts successfully.
- PostgreSQL container is running and connected.
- Redis container is running and connected.
- Kafka container is running at Docker level only.
- Java Kafka producer/consumer integration has not been implemented yet.
- No trading, order, account, user, matching, or AI module has been created yet.

### Notes

`/actuator/health` is a Spring Boot infrastructure health endpoint. It is used to check whether the application and connected infrastructure components are healthy.

`/api/system/status` is a custom application endpoint created by this project. It confirms that our own controller layer can return JSON responses correctly.

### Next Task

Prepare Week 2 user/auth/account foundation, but do not start it until Week 1 infrastructure tasks are fully committed and pushed.

## Week 1 Day 3 - Infrastructure Reproducibility Check

Status: Completed

Completed today:

- Updated README.md with Quick Start instructions.
- Documented prerequisites, infrastructure startup, application startup, test command, verified endpoints, Swagger access, and current project boundary.
- Updated API_EXAMPLES.md with currently implemented API endpoints only.
- Verified Actuator health endpoint:
  - GET /actuator/health
- Verified custom system status endpoint:
  - GET /api/system/status
- Verified Swagger UI:
  - http://localhost:8080/swagger-ui/index.html
- Verified OpenAPI JSON:
  - http://localhost:8080/v3/api-docs
- Verified Maven tests:
  - mvn test
- Confirmed that Kafka is still verified at Docker container level only.
- Confirmed that Java Kafka producer and consumer integration has not been implemented yet.

Current implemented endpoints:

- GET /actuator/health
- GET /api/system/status
- Swagger UI: /swagger-ui/index.html
- OpenAPI JSON: /v3/api-docs

Current project boundary:

- No user registration/login yet.
- No JWT authentication yet.
- No account module yet.
- No order module yet.
- No matching engine yet.
- No trade execution yet.
- No Java Kafka producer/consumer yet.
- No Redis business cache yet.
- No AI anomaly detection yet.

Important note:

Week 1 Day 3 is not about adding new business features. It is about making the project reproducible, documented, testable, and ready for Week 2 authentication and account development.

Next task:

Prepare Week 2 user/auth/account foundation. Do not start implementation until Week 1 is fully committed and pushed.

## Week 2 Day 1 Progress

Status: Completed

Completed today:

- Added Flyway V2 migration: `V2__create_user_account_tables.sql`
- Created database tables: `app_users`, `accounts`, `cash_balances`
- Added `AppUser` entity and `UserStatus` enum
- Added `AppUserRepository`
- Added `Account` entity and `AccountStatus` enum
- Added `AccountRepository`
- Added `CashBalance` entity and `CashBalanceRepository`
- Added `AccountPersistenceTest` covering AppUser → Account → CashBalance persistence
- Verified `mvn test` — BUILD SUCCESS
- Pushed code to GitHub

Current project boundary:

- No registration API yet
- No login API yet
- No password hashing service yet
- No JWT authentication yet
- No protected user endpoint yet
- No Kafka business integration yet
- No Redis business cache yet
- No order placement, matching engine, trade execution, or portfolio accounting yet

Next task:

Week 2 Day 2: Register API

## Week 2 Day 2 Progress

Status: Completed

Completed today:

- Added `RegisterRequest` and `RegisterResponse` DTOs
- Added `PasswordEncoder` bean with `BCryptPasswordEncoder`
- Added `DuplicateEmailException` and `AuthExceptionHandler`
- Added `AuthService.register()` with `@Transactional`
- Registration creates `AppUser`, `Account`, and `CashBalance` in one transaction
- Email is normalized with trim + lowercase
- Password is stored as BCrypt hash
- Added `AuthController` with `POST /api/auth/register`
- Added `RegisterApiIntegrationTest`
- Verified `mvn test` — BUILD SUCCESS (8 tests)
- Manual Swagger/API validation passed:
  - HTTP 201 for successful registration
  - HTTP 409 for duplicate email
  - HTTP 400 for blank email

Current project boundary:

- No login yet
- No JWT yet
- No protected endpoint yet
- No Kafka/Redis business integration yet
- No order/matching/trading features yet

Next task:

Week 2 Day 3: Login API
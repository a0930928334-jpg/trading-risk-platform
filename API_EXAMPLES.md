# API Examples

This document records the currently verified API endpoints of the project.

The project is currently in Week 2 authentication and account foundation. Infrastructure endpoints and the Register API are available.

No login, JWT authentication, order, matching, trade, Kafka event, Redis cache, or AI endpoints have been implemented yet.

## 1. Actuator Health Check

Purpose:

Checks whether the Spring Boot application and configured infrastructure health indicators are available.

Request:

    GET /actuator/health

Example curl command:

    curl http://localhost:8080/actuator/health

Expected response:

    {
      "status": "UP"
    }

Notes:

- This endpoint is provided by Spring Boot Actuator.
- It is used to verify application and infrastructure health.
- It is not a custom business API.

## 2. Custom System Status API

Purpose:

Verifies that the project has a custom controller endpoint and can return a JSON response successfully.

Request:

    GET /api/system/status

Example curl command:

    curl http://localhost:8080/api/system/status

Expected response:

    {
      "application": "trading-risk-platform",
      "status": "UP",
      "phase": "INFRASTRUCTURE_SETUP"
    }

Notes:

- This endpoint is implemented by the project.
- It is a minimal custom API for Week 1 infrastructure verification.
- It does not represent trading, order, risk, or authentication functionality.

## 3. User Registration API

Purpose:

Registers a new user with email and password. Creates a default account and zero USD cash balance in a single transaction.

Request:

    POST /api/auth/register
    Content-Type: application/json

Request body:

    {
      "email": "alice@example.com",
      "password": "Password123!",
      "fullName": "Alice Example"
    }

Example PowerShell command:

    $body = @{
      email    = "alice@example.com"
      password = "Password123!"
      fullName = "Alice Example"
    } | ConvertTo-Json

    Invoke-WebRequest `
      -Uri "http://localhost:8080/api/auth/register" `
      -Method POST `
      -ContentType "application/json" `
      -Body $body

Successful response (HTTP 201 Created):

    {
      "userId": 1,
      "email": "alice@example.com",
      "fullName": "Alice Example",
      "accountId": 1,
      "accountNumber": "ACC-000001",
      "currency": "USD"
    }

Duplicate email response (HTTP 409 Conflict):

    {
      "message": "Email already registered: alice@example.com"
    }

Blank field response (HTTP 400 Bad Request):

Returned when required fields such as email are blank or invalid.

Notes:

- Registration does not return a JWT yet.
- Login is not implemented yet.
- Password is stored as a BCrypt hash and is not returned in the response.
- A default account and zero USD cash balance are created during registration.

## Current API Boundary

Implemented:

- GET /actuator/health
- GET /api/system/status
- POST /api/auth/register

Not implemented yet:

- User login
- JWT authentication
- Account API
- Order API
- Matching engine API
- Trade API
- Portfolio API
- Risk control API
- Kafka producer or consumer API
- Redis cache API
- AI anomaly detection API

Important note:

Kafka is currently verified at Docker container level only. Java Kafka producer and consumer integration has not been implemented yet.

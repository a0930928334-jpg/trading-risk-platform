# API Examples

This document records the currently verified API endpoints of the project.

The project is currently in Week 1 infrastructure setup. Only infrastructure-level endpoints are available.

No user, account, order, matching, trade, Kafka event, Redis cache, or AI endpoints have been implemented yet.

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
- It does not represent trading, account, order, risk, or authentication functionality.

## Current API Boundary

Implemented:

- GET /actuator/health
- GET /api/system/status

Not implemented yet:

- User registration
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

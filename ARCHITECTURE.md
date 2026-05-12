# Architecture

## Current Phase

The project is currently in the infrastructure setup phase.

## High-Level Architecture

The system is planned as an event-driven trading and risk platform.

## Main Components

- Spring Boot backend application
- PostgreSQL for persistent trading data
- Redis for cache and fast-access state
- Kafka for event-driven communication
- Flyway for database schema migration
- Docker Compose for local infrastructure

## Current Status

- Spring Boot application can start locally
- PostgreSQL and Redis are connected through Spring Boot Actuator
- Kafka container is running locally
- Kafka Java producer/consumer integration has not been implemented yet

## Planned Modules

- Authentication
- Account management
- Market data
- Order management
- Matching engine
- Trade execution
- Portfolio management
- Risk control
- Event processing

# Java Event-Driven Trading & Risk Platform

A portfolio-level Java backend project for simulating a financial trading and risk management platform.

This project is built to demonstrate backend engineering skills with Java, Spring Boot, PostgreSQL, Redis, Kafka, Flyway, Docker Compose, testing, and later AI-assisted anomaly detection.

This is not a real high-frequency trading exchange. It is a simulated trading and risk platform for learning, portfolio demonstration, and technical interview preparation.

## Tech Stack

- Java 21
- Spring Boot 3
- Maven
- Spring Web
- Spring Security
- Spring Data JPA
- Bean Validation
- PostgreSQL 16
- Redis 7
- Apache Kafka 4.1.2
- Flyway
- Docker Compose
- JUnit 5
- Mockito
- AssertJ
- Spring Boot Test
- Actuator
- Springdoc OpenAPI

## Prerequisites

Before running this project locally, make sure the following tools are installed:

- Java 21
- Maven
- Docker Desktop
- Git

## How to Start Infrastructure

Start PostgreSQL, Redis, and Kafka using Docker Compose:

    docker compose up -d

Check running containers:

    docker ps

Expected containers:

- trading-postgres
- trading-redis
- trading-kafka

## How to Run the Application

Run the Spring Boot application:

    mvn spring-boot:run

The application should start on:

    http://localhost:8080

## How to Run Tests

Run all tests:

    mvn test

Current expected result:

    BUILD SUCCESS

## Verified Endpoints

### Actuator Health Check

Request:

    GET /actuator/health

Expected result:

    {
      "status": "UP"
    }

### Custom System Status API

Request:

    GET /api/system/status

Expected result:

    {
      "application": "trading-risk-platform",
      "status": "UP",
      "phase": "INFRASTRUCTURE_SETUP"
    }

## Swagger / OpenAPI

Swagger UI is available at:

    http://localhost:8080/swagger-ui/index.html

OpenAPI JSON is available at:

    http://localhost:8080/v3/api-docs

Current Week 1 status:

- Swagger UI can be opened successfully.
- The custom system status API is visible in Swagger.
- No complex API annotations are required yet.

## Current Project Boundary

This project is currently in Week 1 infrastructure setup.

Completed:

- Spring Boot project skeleton
- Docker Compose infrastructure
- PostgreSQL container
- Redis container
- Kafka container
- Flyway initial migration
- Actuator health check
- Custom system status API
- First controller tests

Important boundary:

Kafka is currently verified at Docker container level only. Java Kafka producer and consumer integration has not been implemented yet.

Not implemented yet:

- User registration/login
- JWT authentication
- Account and cash balance
- Order placement
- Matching engine
- Trade execution
- Portfolio accounting
- Risk controls
- Java Kafka event publishing/consuming
- Redis business cache
- AI anomaly detection

## Week 1 Goal

Week 1 is not about adding business features.

The goal is to make the project:

- Reproducible
- Documented
- Testable
- Easy to run locally
- Ready for Week 2 authentication and account development

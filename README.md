# Banking System — Microservices Architecture

A Spring Boot microservices-based banking system built to explore real-world distributed systems patterns: service discovery, event-driven communication, caching, and saga-style distributed transactions. It handles internal account-to-account transfers, external payments via Razorpay, rule-based fraud detection, and OTP-based transaction verification.

**Scope note:** This project is focused on microservices architecture, service discovery (Eureka), inter-service communication (Feign + Kafka), and caching/ephemeral state (Redis)

## Architecture Overview

```
                              ┌─────────────────┐
                              │  API Gateway     │  ← single entry point
                              └────────┬─────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    │                  │                  │
            ┌───────▼──────┐  ┌────────▼───────┐  ┌───────▼────────┐
            │   Account     │  │  Transaction   │  │    Payment     │
            │   Service     │  │    Service     │  │    Service     │
            └───────┬───────┘  └────────┬───────┘  └───────┬────────┘
                    │                   │                  │
                    │          ┌────────▼───────┐          │
                    │          │ Fraud Detection │         │
                    │          │    Service      │         │
                    │          └────────┬───────┘          │
                    │                   │                  │
                    │          ┌────────▼───────┐          │
                    └─────────►│ Notification   │◄─────────┘
                               │    Service     │
                               └────────────────┘

              All services register with:  Registry Service (Eureka)
              Async communication via:     Apache Kafka
              Caching / OTP / rate-limits: Redis
              Persistence:                 MySQL
```

## Services

| Service | Responsibility |
|---|---|
| **registry-service** | Eureka server — service discovery for all microservices |
| **api-gateway-service** | Single entry point for all client requests; routes to backend services via Eureka |
| **account-service** | Manages user accounts — creation, balance, debit/credit operations, account status |
| **transaction-service** | Handles internal account-to-account transfers; orchestrates the fraud-check + OTP verification saga |
| **fraud-detection-service** | Analyzes transactions for suspicious patterns (velocity, unusual amount, balance percentage) using rule-based checks, backed by Redis |
| **payment-service** | Handles external money movement — deposits/withdrawals via Razorpay integration |
| **notification-service** | Sends email notifications (OTPs, transaction alerts, fraud alerts) via Kafka event consumption |

## Tech Stack

- **Language / Framework:** Java 17, Spring Boot
- **Service Discovery:** Netflix Eureka (Spring Cloud)
- **API Gateway:** Spring Cloud Gateway (reactive)
- **Messaging:** Apache Kafka (event-driven communication between services)
- **Caching / Ephemeral state:** Redis (OTP storage, fraud velocity checks)
- **Database:** MySQL
- **Inter-service sync calls:** OpenFeign
- **Payment gateway:** Razorpay
- **Containerization:** Docker / Docker Compose

## How a Transfer Works (Saga Flow)

1. Client sends a transfer request to **Transaction Service** via the **API Gateway**
2. Transaction Service checks sender's balance via **Account Service** (Feign)
3. Transaction Service triggers a **fraud check** via **Fraud Detection Service** (Kafka + Redis-backed velocity/pattern checks)
4. **If fraud is detected** → transaction is rejected, nothing is debited
5. **If verification is required** → an OTP is generated, stored in Redis (5-minute expiry), and sent to the user via **Notification Service**
6. User submits the OTP → Transaction Service verifies it against Redis
7. On successful verification → sender is debited, receiver is credited (via Account Service), transaction marked `COMPLETED`
8. If OTP expires or is never submitted → a scheduled sweeper reverses/expires the transaction

## Getting Started

### Prerequisites

- Java 17+
- Maven
- Docker & Docker Compose

### 1. Start infrastructure (MySQL, Redis, Kafka)

```bash
docker-compose up -d
```

### 2. Start services (in order)

```bash
# 1. Registry Service (Eureka) — must start first
cd registry-service && mvn spring-boot:run

# 2. Start remaining services (any order, once registry is up)
cd account-service && mvn spring-boot:run
cd transaction-service && mvn spring-boot:run
cd fraud-detection-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run

# 3. API Gateway — start last, once backend services are registered
cd api-gateway-service && mvn spring-boot:run
```

### 3. Verify services are registered

Open the Eureka dashboard: [http://localhost:8761](http://localhost:8761)

All services should appear as `UP`.

### 4. Access the system

All requests go through the API Gateway:

```
http://localhost:<gateway-port>/api/v1/accounts
http://localhost:<gateway-port>/api/v1/transactions
http://localhost:<gateway-port>/api/v1/payments
```

## Configuration

Each service requires its own `application.properties` with, at minimum:

```properties
spring.application.name=<service-name>
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
eureka.instance.prefer-ip-address=true
```

Services connecting to MySQL/Redis/Kafka should point to the ports exposed in `docker-compose.yml`.

Payment Service additionally requires Razorpay credentials:

```properties
razorpay.key.id=<your-key-id>
razorpay.key.secret=<your-key-secret>
razorpay.webhook.secret=<your-webhook-secret>
```

**Note:** Do not commit real credentials — use environment variables or a `.env` file excluded via `.gitignore`.

## Project Status

This is a learning project focused on understanding microservices patterns — service discovery, API gateways, event-driven architecture, and distributed transaction handling.


## License

This project is for educational purposes.

# Reactive Order Microservice Architecture

A production-grade, event-driven order processing system built with **Spring WebFlux** and **Apache Kafka**. Implements the **Saga pattern** for distributed transactions with automatic compensation, fully reactive (non-blocking) end-to-end.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.9.0-black)
![Redis](https://img.shields.io/badge/Redis-7-red)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17.5-blue)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)
![Kubernetes](https://img.shields.io/badge/Kubernetes-Ready-326CE5)

---

## Architecture Overview

![Reactive_Order_Microservice_Architecture.png](diagram/Reactive_Order_Microservice_Architecture.png)

Each service owns its **PostgreSQL database** (database-per-service pattern) and communicates asynchronously via **Kafka events**.

---

## Key Features

| Feature | Implementation |
|---------|---------------|
| **Reactive End-to-End** | Spring WebFlux + R2DBC (zero blocking calls) |
| **Saga Pattern** | Orchestrator-based with stateful coordination |
| **Compensation Transactions** | Auto-rollback on failures (refund, stock release) |
| **Event-Driven** | 13 Kafka topics connecting 6 services |
| **JWT Security (RSA)** | Auth-service issues, Gateway validates, role-based access |
| **Idempotency** | Redis-based deduplication with 24h TTL |
| **Event Deduplication** | Redis checkpoint prevents duplicate Kafka processing |
| **Distributed Tracing** | X-Correlation-Id propagated across HTTP & Kafka |
| **Race Condition Handling** | Conditional DB updates (optimistic concurrency) |
| **Order Expiry** | Scheduler auto-expires unpaid orders, releases stock |
| **Payment Expiry** | Payment-service scheduler expires stale PENDING payments |
| **Audit Trail** | Immutable ledger tables (order, stock, payment) |
| **Kubernetes-Native** | No Eureka/Config Server — uses K8s service discovery, ConfigMaps, and Secrets |
| **Connection Pooling** | PgBouncer (transaction mode) prevents connection exhaustion on auto-scaling |

---

## Services

| Service | Port | Responsibility |
|---------|------|---------------|
| **gateway-service** | 8080 | API Gateway, JWT validation, authorization, routing, rate limiting |
| **auth-service** | 8081 | Login, registration, JWT generation (RSA), refresh tokens |
| **order-service** | 8082 | Order creation, status tracking, discount application |
| **inventory-service** | 8083 | Stock reservation, release, deduction |
| **payment-service** | 8084 | Payment initiation, webhook callbacks, refunds, payment expiry scheduler |
| **orchestrator-service** | 8085 | Saga coordinator, decision engine, order expiry scheduler |
| **common-lib** | — | Shared JWT utilities, exception handling, Redis utilities |

---

## Order Flow (Happy Path)

```
Client                Order         Inventory       Orchestrator      Payment
  │                     │               │               │               │
  │─── Create Order ───►│               │               │               │
  │◄── transactionId ───│               │               │               │
  │                     │── RESERVE ───►│               │               │
  │                     │               │── RESERVED ──►│               │
  │                     │◄── RESERVED ──│ (same topic)  │               │
  │                     │(WAITING_PAYMENT)              │               │
  │─── Create Payment ─────────────────────────────────────────────────►│
  │◄── paymentUrl ──────────────────────────────────────────────────────│
  │─── Webhook Success ─────────────────────────────────────────────────►│
  │                     │               │               │◄── PAID ──────│
  │                     │◄── PAID ──────────────────────│ (same topic)  │
  │                     │               │◄─ DEDUCT ─────│               │
  │                     │◄── COMPLETED ─────────────────│               │
  │                     │               │               │               │
```

### Order Status Lifecycle

```
PENDING → WAITING_PAYMENT → PAID → COMPLETED
                │                      │
                ├→ OUT_OF_STOCK        ├→ REFUNDED (if stock fails after payment)
                │                      └→ REFUND_FAILED
                └→ EXPIRED (timeout)
```

---

## Compensation Flows

### Out of Stock (Payment Already Made)
```
Payment SUCCESS + Stock INSUFFICIENT → Orchestrator triggers REFUND → Order REFUNDED
```

### Order Expired (User Never Paid)
```
Stock RESERVED + No payment within timeout → Scheduler triggers RELEASE_STOCK → Order EXPIRED
```

### Order Expired (User Already Paid, Stock Stuck)
```
Stock NOT reserved + Payment PAID + Timeout → Scheduler triggers REFUND → Order EXPIRED
```

### Out of Stock (Payment In Progress — Race Condition)
```
Stock OUT_OF_STOCK + Payment INITIATED (in progress) → Orchestrator waits →
  → Payment SUCCESS arrives → Orchestrator sees OUT_OF_STOCK → triggers REFUND → Order REFUNDED
  → Payment FAILED arrives → Log only (saga already waiting, will expire via scheduler)
```

### Payment Expired (Payment Gateway Timeout)
```
Payment PENDING for > timeout → Payment-service scheduler marks FAILED → produces PAYMENT_FAILED →
  → Orchestrator receives PAYMENT_FAILED → logs (user can retry)
  → Next orchestrator scheduler cycle: saga no longer INITIATED → marks FAILED + ORDER_EXPIRED
```

### Payment Failed (User Can Retry)
```
Payment FAILED → Order stays WAITING_PAYMENT → User picks new payment method →
  → Cancel existing PENDING payment → Create new payment → New attempt
```

### Late Webhook After Payment Expired (Race Condition)
```
Payment expired (marked FAILED) + Late PAYMENT_SUCCESS webhook arrives →
  → Payment-service sees status=FAILED → triggers silent refund → no event produced
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Java 21 |
| **Framework** | Spring Boot 4.1.0, Spring WebFlux |
| **Gateway** | Spring Cloud Gateway 2025.1.1 |
| **Database** | PostgreSQL 17.5 (R2DBC — reactive) |
| **Messaging** | Apache Kafka (Confluent 7.9.0) |
| **Cache** | Redis 7 (reactive) |
| **Security** | JWT with RSA key pair (JJWT 0.12.6) |
| **Build** | Maven (multi-module) |
| **Containers** | Docker / Docker Compose |
| **Orchestration** | Kubernetes |
| **Connection Pooler** | PgBouncer 1.21+ (transaction mode) |
| **API Docs** | SpringDoc OpenAPI 2.8.13 |

---

## Quick Start

### Prerequisites

- Java 21+
- Docker & Docker Compose

### One-Command Setup (Docker)

```bash
# Windows
start.bat

# Linux/Mac
chmod +x start.sh && ./start.sh
```

Or manually:
```bash
mvn clean package -DskipTests
docker compose -f docker-compose.full.yml up --build -d
```
### Manual Setup (Local Development)

#### 1. Start Infrastructure

```bash
# Start Kafka, Zookeeper, and Redis
docker compose up -d

# Start all service databases
docker compose -f auth-service/docker-compose.yml up -d
docker compose -f order-service/docker-compose.yml up -d
docker compose -f inventory-service/docker-compose.yml up -d
docker compose -f payment-service/docker-compose.yml up -d
docker compose -f orchestrator-service/docker-compose.yml up -d
```

#### 2. Build the Project

```bash
./mvnw clean install -DskipTests
```

#### 3. Run Services

Start each service in a separate terminal:

```bash
cd auth-service && ../mvnw spring-boot:run
cd gateway-service && ../mvnw spring-boot:run
cd order-service && ../mvnw spring-boot:run
cd inventory-service && ../mvnw spring-boot:run
cd payment-service && ../mvnw spring-boot:run
cd orchestrator-service && ../mvnw spring-boot:run
```

### 4. Access

- **Gateway**: http://localhost:8080
- **Swagger UI (order-service)**: http://localhost:8082/swagger-ui.html
- **Swagger UI (payment-service)**: http://localhost:8084/swagger-ui.html
- **Swagger UI (auth-service)**: http://localhost:8081/swagger-ui.html
- **Swagger UI (inventory-service)**: http://localhost:8083/swagger-ui.html (internal only)

---

## API Endpoints

### Auth Service (Public)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/auth/login` | User login |
| POST | `/api/v1/auth/register` | User registration |
| POST | `/api/v1/auth/refresh` | Refresh access token |

### Order Service (Authenticated)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/orders` | Create order (requires `X-Idempotency-Key`) | ✅ |
| GET | `/api/v1/orders/status/{transactionId}` | Get order status | ✅ |
| GET | `/api/v1/orders` | Get user's orders | ✅ |

### Payment Service

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/payments` | Create payment | ✅ |
| GET | `/api/v1/payments/list` | Get user's payments | ✅ |
| GET | `/api/v1/payments/status/{transactionId}` | Get payment status | ✅ |
| POST | `/api/v1/payments/webhook/callback` | Payment webhook callback | Public |

### Inventory Service (Internal Only — Not Exposed via Gateway)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/products/list` | Get products by IDs (internal, called by order-service) |

> ⚠️ Inventory-service has no public-facing API. All endpoints are internal service-to-service calls within the cluster.

---

## Kafka Event Flow

![Kafka_Event_Flow.drawio.png](diagram/Kafka_Event_Flow.drawio.png)

## Saga State Machine

The orchestrator tracks each transaction's progress and handles events arriving in any order:

| Event Received             | Current State              | Action                              |
|----------------------------|----------------------------|-------------------------------------|
| STOCK_RESERVED             | payment=PAID               | → ORDER_COMPLETED + DEDUCT_STOCK    |
| STOCK_RESERVED             | payment=NULL               | → Wait for payment                  |
| PAYMENT_COMPLETED          | stock=RESERVED             | → ORDER_COMPLETED + DEDUCT_STOCK    |
| PAYMENT_COMPLETED          | stock=NULL                 | → Wait for stock result             |
| PAYMENT_COMPLETED          | stock=OUT_OF_STOCK         | → REFUND_REQUESTED (compensation)   |
| OUT_OF_STOCK               | payment=PAID               | → REFUND_REQUESTED (compensation)   |
| OUT_OF_STOCK               | payment=NULL               | → Saga FAILED (no action needed)    |
| OUT_OF_STOCK               | payment=INITIATED          | → Wait for payment result           |
| PAYMENT_FAILED             | any                        | → Log only (user can retry payment) |
| ORDER_REFUND_COMPLETED     | saga=COMPENSATING          | → Saga COMPLETED (compensation done)|
| ORDER_REFUND_FAILED        | saga=COMPENSATING          | → Saga FAILED (manual intervention) |
| Order Expired (scheduler)  | stock=RESERVED, no payment | → ORDER_EXPIRED + RELEASE_STOCK     |
| Order Expired (scheduler)  | no stock, payment=PAID     | → ORDER_EXPIRED + REFUND_REQUESTED  |
| Order Expired (scheduler)  | no stock, no payment       | → ORDER_EXPIRED                     |
| Order Expired (scheduler)  | payment=INITIATED          | → Skip (wait for payment to resolve) |
| Payment Expired (scheduler) | payment=PENDING > timeout  | → Mark FAILED + produce PAYMENT_FAILED |

---

## Project Structure

```
reactive-order-microservice/
├── common-lib/              # Shared: JWT, exceptions, Redis utilities
├── auth-service/            # Authentication & user management
├── gateway-service/         # API Gateway with JWT + rate limiting, Authorization
├── order-service/           # Order processing + discount engine
├── inventory-service/       # Stock reservation & management
├── payment-service/         # Payment processing, refunds + payment expiry scheduler
├── orchestrator-service/    # Saga coordinator + order expiry scheduler
├── docker-compose.yml       # Infrastructure (Kafka, Zookeeper, Redis)
└── pom.xml                  # Parent POM (multi-module Maven)
```

Each service follows a consistent internal structure:
```
service/src/main/java/com/MSyamsandiYW/<service_name>/
├── config/          # Bean configurations (Kafka, R2DBC, Redis, JWT)
├── kafka/           # Kafka producers, consumers, event handlers
├── <domain>/        # Entity, Controller, Service, Repository, DTOs
├── handler/         # Global exception handling
├── properties/      # AppConstant, AppProperties
└── Application.java
```

---

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| **R2DBC over JPA** | Fully non-blocking DB access for reactive stack |
| **Orchestrator Saga** (not Choreography) | Single source of truth for saga decisions, easier to debug |
| **Conditional DB update** for race conditions | Optimistic concurrency — whoever commits first wins |
| **Redis event deduplication** | Prevents duplicate processing on Kafka redelivery |
| **Gateway-first security** | Single entry point for auth; internal services trust headers |
| **Auth behind Gateway** | Simplifies K8s Ingress config (single entry point, one cluster) |
| **No Eureka/Config Server** | Kubernetes provides service discovery and config natively |
| **Database-per-service** | Full autonomy, independent scaling and schema evolution |
| **Ledger tables** | Immutable audit trail for compliance and debugging |
| **Payment method switching** | Cancel existing PENDING payment, create new one (no double-charge) |
| **Payment expiry in payment-service** | Payment owns its lifecycle; orchestrator skips INITIATED sagas until payment resolves |
| **PgBouncer + R2DBC** | R2DBC uses unnamed prepared statements — no stale state on connection reassignment, perfect for PgBouncer transaction mode |

---

## Enterprise Patterns Implemented

- ✅ Saga Pattern (Orchestrator-based)
- ✅ Compensation Transactions
- ✅ Event-Driven Architecture
- ✅ Database-per-Service
- ✅ API Gateway Pattern
- ✅ JWT Authentication (RSA)
- ✅ Role-Based Access Control
- ✅ Idempotency Pattern
- ✅ Event Deduplication
- ✅ Distributed Tracing (Correlation ID)
- ✅ Strategy Pattern (Discount Engine)
- ✅ Event Sourcing (Ledger Tables)
- ✅ Optimistic Concurrency Control
- ✅ Circuit Breaker (Resilience4j)
- ✅ Rate Limiting
- ✅ Dead Letter Queue (DLQ)
- ✅ Connection Pooling (PgBouncer)

---

## PgBouncer — Connection Pooling for Auto-Scaling

Auto-scaling pods × connections per pod = connection explosion. PgBouncer multiplexes all pod connections into a small pool of actual DB connections.

```
[Pod 1] ─┐                              ┌─ [PostgreSQL]
[Pod 2] ─┼─→ [PgBouncer (max 20)] ─────→│  (max_connections=100)
[Pod 3] ─┤                              └─
[Pod N] ─┘
```

**Why R2DBC + PgBouncer works perfectly:** R2DBC uses unnamed prepared statements (`PARSE name=""`) — no state persists on the server connection, so PgBouncer can freely reassign connections between transactions.

---

## Kubernetes Deployment

The system is designed to be Kubernetes-native:

- **Service Discovery**: K8s DNS (no Eureka)
- **Configuration**: ConfigMaps + Secrets (no Config Server)
- **Scaling**: Horizontal Pod Autoscaler per service
- **Connection Pooling**: PgBouncer per service DB (prevents connection exhaustion)
- **Health Checks**: Spring Actuator readiness/liveness probes

```bash
# Deploy to Kubernetes
kubectl apply -f k8s/
```

---

## Testing

```bash
# Run unit tests
./mvnw test

# Run integration tests (requires Docker for TestContainers)
./mvnw verify -P integration-tests
```

---

## Environment Variables

Each service uses `.env` files for local development. See `.env.example` in each service directory.

| Variable | Description |
|----------|-------------|
| `DB_HOST` | PostgreSQL host |
| `DB_PORT` | PostgreSQL port |
| `DB_NAME` | Database name |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address |
| `REDIS_HOST` | Redis host |
| `REDIS_PORT` | Redis port |

---

## Payment Webhook State Machine

The payment-service validates webhook callbacks against the current payment state using `paymentId` 

```
Webhook PAYMENT_SUCCESS:
├── Payment is PENDING       → mark SUCCESS, produce PAYMENT_COMPLETED
├── Payment is CANCELLED     → trigger silent refund to provider, no event produced
├── Payment is FAILED        → trigger silent refund to provider, no event produced (expired but charged)
├── Any other status         → ignore (log warning)

Webhook PAYMENT_FAILED:
├── Payment is PENDING       → mark FAILED, produce PAYMENT_FAILED
├── Any other status         → ignore (log warning)

Webhook REFUND_SUCCESS:
├── Payment is CANCELLED     → mark REFUNDED, no event produced (silent refund)
├── Payment is SUCCESS       → mark REFUNDED, produce ORDER_REFUND_COMPLETED
├── Payment is REFUND_FAILED → mark REFUNDED, produce ORDER_REFUND_COMPLETED (retry succeeded)
├── Any other status         → ignore (log warning)

Webhook REFUND_FAILED:
├── Payment is CANCELLED     → mark REFUND_FAILED, produce ORDER_REFUND_FAILED
├── Payment is SUCCESS       → mark REFUND_FAILED, produce ORDER_REFUND_FAILED
├── Any other status         → ignore (log warning)
```

---

## Roadmap

### ✅ Phase 1 — MVP Foundation (Complete)
- Reactive microservices with Spring WebFlux + R2DBC
- Saga pattern (orchestrator-based) with compensation transactions
- Event-driven architecture with 13 Kafka topics
- JWT authentication (RSA) with gateway validation
- Database-per-service with audit trail (ledger tables)
- Idempotency & event deduplication (Redis)
- Order expiry scheduler with automatic stock release/refund
- Optimistic concurrency control (conditional DB updates)
- Docker Compose full-stack deployment

### ✅ Phase 2 — Production Hardening (Complete)
- Rate limiting at API Gateway
- Circuit breaker (Resilience4j)
- Dead Letter Queue (DLQ) for failed messages
- PgBouncer connection pooling (transaction mode)
- Unit tests with Mockito + StepVerifier (~50 test cases)
- API documentation (Swagger/OpenAPI + Postman collection)

### 🔲 Phase 3 — Observability & CI/CD (Planned)
- Distributed tracing with Micrometer + Zipkin/Jaeger
- Prometheus + Grafana metrics dashboard
- Centralized logging (ELK Stack or Loki)
- GitHub Actions CI/CD pipeline
- SonarQube code quality integration
- Integration tests with TestContainers

### 🔲 Phase 4 — Cloud Deployment (Planned)
- Kubernetes manifests (Deployments, Services, Ingress)
- Horizontal Pod Autoscaler per service
- ConfigMaps + Secrets for environment management
- AWS deployment (ECS or EKS)
- Terraform infrastructure-as-code

### 🔲 Phase 5 — Advanced Patterns (Future)
- Event sourcing with CQRS
- GraphQL API layer
- OAuth2/OIDC integration (Keycloak)
- Multi-region deployment
- Kafka Streams for real-time analytics

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

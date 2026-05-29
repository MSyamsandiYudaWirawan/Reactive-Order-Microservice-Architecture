# Reactive Order Microservice Architecture Notes

## Tech Stack

### Core Technologies

- Spring WebFlux
- Apache Kafka
- Redis
- Docker
- Kubernetes
- PostgreSQL
- JWT Authentication
- Reactive WebClient

## Architecture Goals

- Reactive microservices
- Event-driven architecture
- Saga pattern
- Compensation transaction
- Idempotency
- Distributed tracing
- Cloud-native deployment
- JWT authentication & authorization

---

## Service List

### 1. auth-service

**Responsibilities:**

- Login
- Refresh token
- JWT generation
- User credential validation

**Important Notes:**

- Does NOT handle authorization logic
- Only responsible for authentication
- JWT contains: `userId`, `role`, `email`

**Endpoints:**

| Method | Path           | Description   |
|--------|----------------|---------------|
| POST   | /auth/login    | User login    |
| POST   | /auth/refresh  | Refresh token |

---

### 2. gateway-service

**Responsibilities:**

- API Gateway
- JWT validation
- Authorization
- Route requests
- Generate correlationId
- Propagate JWT downstream

**Authorization Example:**

| Endpoint     | Role  |
|--------------|-------|
| POST /orders | USER  |
| POST /refund | ADMIN |

**Important Notes:**

- Gateway is the main security layer
- Internal services are private inside Kubernetes network

**JWT Flow:**

```
Client → [JWT] → Gateway validates JWT → forwards JWT → Downstream services
```

**Correlation ID:**

- Gateway generates `X-Correlation-Id` and propagates it to:
  - Downstream HTTP calls
  - Kafka events
  - Logs

---

### 3. order-service

**Responsibilities:**

- Create order
- Store order in database
- Create checkpoint in Redis
- Publish `RESERVE_STOCK` event
- Return `transactionId` to client
- Consume status update events to sync own DB

**Order Status:**

| Status          |
|-----------------|
| PENDING         |
| WAITING_PAYMENT |
| PAID            |
| FAILED          |
| COMPLETED       |
| REFUNDED        |

**Flow:**

```
Client → Create Order → Save DB → Redis checkpoint → Publish RESERVE_STOCK → Return transactionId
```

**Kafka Consumer:**

| Event            | Action                      |
|------------------|-----------------------------|
| ORDER_COMPLETED  | Update status → COMPLETED   |
| ORDER_FAILED     | Update status → FAILED      |
| PAYMENT_REFUNDED | Update status → REFUNDED    |

**Important Notes:**

- User information extracted from JWT
- Correlation ID stored in logs/events
- Does NOT wait for stock reservation (optimistic)

---

### 4. inventory-service

**Responsibilities:**

- Reserve stock
- Release stock

**Kafka Consumer:**

| Event         | Action                                    |
|---------------|-------------------------------------------|
| RESERVE_STOCK | Reserve stock                             |
| RELEASE_STOCK | Release stock (cancel reservation)        |
| DEDUCT_STOCK  | Deduct stock (convert reservation to sold)|

**Kafka Producer:**

| Event        | Condition          |
|--------------|--------------------|
| OUT_OF_STOCK | Stock insufficient |

**Important Notes:**

- Inventory reservation is part of saga pattern

---

### 5. payment-service

**Responsibilities:**

- Accept payment request with `transactionId` + payment method
- Support 3 dummy payment methods (simulating real-world multiple payment types)
- Handle payment callback/webhook
- Refund payment
- Publish payment event

**Endpoints:**

| Method | Path                            | Description            |
|--------|---------------------------------|------------------------|
| POST   | /payment                        | Initiate payment       |
| POST   | /payment/callback/success       | Success callback       |
| POST   | /payment/callback/failed        | Failed callback        |

**Dummy Payment Response:**

```json
{
  "transactionId": "order-123",
  "paymentMethod": "BANK_TRANSFER",
  "successUrl": "/payment/callback/success?orderId=123",
  "failedUrl": "/payment/callback/failed?orderId=123"
}
```

**Kafka Producer:**

| Event            | Condition        |
|------------------|------------------|
| PAYMENT_SUCCESS  | Callback success |
| PAYMENT_FAILED   | Callback failed  |
| PAYMENT_REFUNDED | Refund completed |

**Kafka Consumer:**

| Event            | Action                                      |
|------------------|---------------------------------------------|
| REFUND_REQUESTED | Process refund → Publish PAYMENT_REFUNDED   |

---

### 6. fulfillment-service

**Responsibilities:**

- Saga coordinator for all outcomes
- Decide final order result
- Handle all compensation logic
- (Future) Call third-party API for actual fulfillment (e.g. shipping, purchasing from supplier)

**Kafka Consumer:**

| Event           | Source            |
|-----------------|-------------------|
| PAYMENT_SUCCESS | payment-service   |
| PAYMENT_FAILED  | payment-service   |
| OUT_OF_STOCK    | inventory-service |

**Saga Logic (current — coordinator only):**

| Condition                    | Actions                                  |
|------------------------------|------------------------------------------|
| Payment success              | Publish ORDER_COMPLETED + DEDUCT_STOCK   |
| Payment failed               | Publish ORDER_FAILED + RELEASE_STOCK     |
| Out of stock (no payment)    | Publish ORDER_FAILED                     |
| Out of stock (payment exists)| Publish REFUND_REQUESTED + ORDER_FAILED  |

**Saga Logic (future — with third-party fulfillment):**

| Condition                    | Actions                                        |
|------------------------------|------------------------------------------------|
| Payment success              | Call third-party API to fulfill                 |
| Fulfillment success          | Publish ORDER_COMPLETED + DEDUCT_STOCK         |
| Fulfillment failed           | Publish REFUND_REQUESTED + RELEASE_STOCK       |
| Payment failed               | Publish ORDER_FAILED + RELEASE_STOCK           |
| Out of stock (no payment)    | Publish ORDER_FAILED                           |
| Out of stock (payment exists)| Publish REFUND_REQUESTED + ORDER_FAILED        |

**Kafka Producer:**

| Event             | Condition                                |
|-------------------|------------------------------------------|
| DEDUCT_STOCK      | Payment success / Fulfillment success    |
| RELEASE_STOCK     | Payment failed / Fulfillment failed      |
| REFUND_REQUESTED  | Out of stock (paid) / Fulfillment failed |
| ORDER_COMPLETED   | Payment success / Fulfillment success    |
| ORDER_FAILED      | Payment failed / Out of stock            |

**Important Notes:**

- This is the single source of truth for saga decisions
- No scattered decision-making across services
- Currently acts as coordinator only — no external API calls
- Future: add third-party fulfillment (shipping, supplier purchase) which introduces real "fulfillment failed" scenario

---

## Complete Flows

### Flow 1: Happy Path (Order Success)

```
1. Client → order-service: Create order → Save DB (PENDING) → Redis checkpoint → Publish RESERVE_STOCK → Return transactionId
2. inventory-service: Consumes RESERVE_STOCK → Reserve stock ✅
3. Client → payment-service: Send transactionId + payment method → Return dummy callbacks
4. Client → payment callback success → payment-service: Publish PAYMENT_SUCCESS
5. fulfillment-service: Consumes PAYMENT_SUCCESS → Publish ORDER_COMPLETED + DEDUCT_STOCK
6. inventory-service: Consumes DEDUCT_STOCK → Convert reservation to sold ✅
7. order-service: Consumes ORDER_COMPLETED → Update status → COMPLETED ✅
```

### Flow 2: Payment Failed

```
1. Client → order-service: Create order → Save DB (PENDING) → Redis checkpoint → Publish RESERVE_STOCK → Return transactionId
2. inventory-service: Consumes RESERVE_STOCK → Reserve stock ✅
3. Client → payment-service: Send transactionId + payment method → Return dummy callbacks
4. Client → payment callback failed → payment-service: Publish PAYMENT_FAILED
5. fulfillment-service: Consumes PAYMENT_FAILED → Publish ORDER_FAILED + RELEASE_STOCK
6. inventory-service: Consumes RELEASE_STOCK → Cancel reservation ✅
7. order-service: Consumes ORDER_FAILED → Update status → FAILED ✅
```

### Flow 3: Out of Stock (No Payment Yet)

```
1. Client → order-service: Create order → Save DB (PENDING) → Redis checkpoint → Publish RESERVE_STOCK → Return transactionId
2. inventory-service: Consumes RESERVE_STOCK → Stock insufficient → Publish OUT_OF_STOCK
3. fulfillment-service: Consumes OUT_OF_STOCK → No payment exists → Publish ORDER_FAILED
4. order-service: Consumes ORDER_FAILED → Update status → FAILED ✅
```

### Flow 4: Out of Stock (Payment Already Made)

```
1. Client → order-service: Create order → Save DB (PENDING) → Redis checkpoint → Publish RESERVE_STOCK → Return transactionId
2. Client → payment-service: Send transactionId + payment method → callback success → Publish PAYMENT_SUCCESS
3. inventory-service: Consumes RESERVE_STOCK → Stock insufficient → Publish OUT_OF_STOCK
4. fulfillment-service: Consumes OUT_OF_STOCK → Payment exists → Publish REFUND_REQUESTED + ORDER_FAILED
5. payment-service: Consumes REFUND_REQUESTED → Process refund → Publish PAYMENT_REFUNDED
6. order-service: Consumes ORDER_FAILED → Update status → FAILED
7. order-service: Consumes PAYMENT_REFUNDED → Update status → REFUNDED ✅
```

### Flow 5: Fulfillment Failed (Future — with third-party API)

```
1. Client → order-service: Create order → Save DB (PENDING) → Redis checkpoint → Publish RESERVE_STOCK → Return transactionId
2. inventory-service: Consumes RESERVE_STOCK → Reserve stock ✅
3. Client → payment-service: Send transactionId + payment method → callback success → Publish PAYMENT_SUCCESS
4. fulfillment-service: Consumes PAYMENT_SUCCESS → Call third-party API → Fails ❌ → Publish REFUND_REQUESTED + RELEASE_STOCK
5. payment-service: Consumes REFUND_REQUESTED → Process refund → Publish PAYMENT_REFUNDED
6. inventory-service: Consumes RELEASE_STOCK → Cancel reservation ✅
7. order-service: Consumes PAYMENT_REFUNDED → Update status → REFUNDED ✅
```



---

## Infrastructure Components

### Redis

**Responsibilities:**

- Idempotency
- Deduplication
- Event checkpoint

**Deduplication Flow:**

```
Receive Kafka event → Check Redis → Already exists? 
  → Yes: Ignore
  → No: Process event → Save eventId
```

**Recommended TTL:** 24 hours

---

### Kafka Topics

| Topic              | Producer            | Consumer            |
|--------------------|---------------------|---------------------|
| reserve-stock      | order-service       | inventory-service   |
| deduct-stock       | fulfillment-service | inventory-service   |
| release-stock      | fulfillment-service | inventory-service   |
| out-of-stock       | inventory-service   | fulfillment-service |
| payment-success    | payment-service     | fulfillment-service |
| payment-failed     | payment-service     | fulfillment-service |
| refund-requested   | fulfillment-service | payment-service     |
| payment-refunded   | payment-service     | order-service       |
| order-completed    | fulfillment-service | order-service       |
| order-failed       | fulfillment-service | order-service       |

**Note:** order-service is both a producer (`reserve-stock`) and consumer (`order-completed`, `order-failed`, `payment-refunded`) because each service owns its own DB — status updates must flow back via events.

---

### Correlation ID

**Purpose:** Distributed tracing across services

**Header:** `X-Correlation-Id`

**Must be propagated to:**

- HTTP requests
- Kafka events
- Logs

**Example Event:**

```json
{
  "eventId": "abc-123",
  "correlationId": "corr-999",
  "orderId": "order-1"
}
```

---

### JWT Propagation

**Strategy:**

1. Gateway validates JWT
2. Gateway forwards JWT downstream

**Benefits:** Services can access `userId`, `role`, `email` without calling auth-service again.

---

## Kubernetes Architecture

### No Eureka Needed

Kubernetes already provides:

- Service discovery
- DNS resolution
- Load balancing

```
http://payment-service  ← works automatically inside cluster
```

### Configuration Management

**No Spring Config Server** — Use Kubernetes-native alternatives:

| Type        | Use Case                              |
|-------------|---------------------------------------|
| ConfigMap   | Non-sensitive config (timeouts, URLs) |
| Secret      | DB password, JWT keys, Kafka creds    |
| Environment | Injected into pods                    |

**ConfigMap Example:**

```yaml
env:
  - name: APP_PAYMENT_TIMEOUT
    value: "30"
```

Spring Boot automatically maps `APP_PAYMENT_TIMEOUT` → `app.payment-timeout`

**Secrets Example:**

Use Kubernetes Secret for:

- DB password
- JWT private/public keys
- Kafka credentials

---

## Recommended Development Phases

### Phase 1 — MVP

- JWT auth
- Gateway authorization
- Create order
- Kafka event flow
- Dummy payment callback (3 payment methods)
- Fulfillment flow
- **No Kubernetes yet** — Use Docker Compose first

### Phase 2 — Reliability

- Redis idempotency
- Retry
- Dead Letter Queue (DLQ)
- Compensation flow

### Phase 3 — Observability

- Correlation ID logging
- Distributed tracing
- Metrics
- Centralized logging

### Phase 4 — Kubernetes

- Kubernetes deployment
- ConfigMap & Secret
- Horizontal scaling

---

## Project Structure

```
├── auth-service/
├── gateway-service/
├── order-service/
├── inventory-service/
├── payment-service/
├── fulfillment-service/
└── infra/
    ├── docker-compose.yml
    └── k8s/
        ├── kafka/
        ├── redis/
        └── postgresql/
```

---

## Enterprise Concepts Used

| Concept                    | Used |
|----------------------------|------|
| JWT Authentication         | ✅   |
| Authorization              | ✅   |
| API Gateway                | ✅   |
| Reactive Programming       | ✅   |
| Saga Pattern               | ✅   |
| Compensation Transaction   | ✅   |
| Event-Driven Architecture  | ✅   |
| Kafka Messaging            | ✅   |
| Idempotency                | ✅   |
| Distributed Tracing        | ✅   |
| Correlation ID             | ✅   |
| Kubernetes                 | ✅   |
| Docker                     | ✅   |

---

## Future Improvements (Optional)

After MVP is stable:

- Outbox Pattern
- mTLS
- Service Mesh
- OpenTelemetry
- Circuit Breaker
- Rate Limiter
- Multi-tenancy
- CQRS / Event Sourcing
- gRPC internal communication

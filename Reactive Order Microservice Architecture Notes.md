# Reactive Order Microservice Architecture Notes

## Tech Stack

### Core Technologies

- Java 21
- Spring Boot 4.0.6
- Spring WebFlux
- Spring Cloud Gateway 2025.1.1
- Apache Kafka (Confluent 7.9.0)
- Redis 7-alpine
- Docker / Docker Compose
- Kubernetes (planned - Phase 4)
- PostgreSQL 17.5
- JWT Authentication (RSA key pair, JJWT 0.12.6)
- R2DBC (Reactive database access)
- Reactive WebClient

## Architecture Goals

- Reactive microservices (non-blocking end-to-end)
- Event-driven architecture
- Saga pattern (fulfillment-service as coordinator)
- Compensation transaction
- Idempotency & deduplication (Redis-based, 24h TTL)
- Distributed tracing (X-Correlation-Id)
- Cloud-native deployment (K8s, no Eureka/Config Server)
- JWT authentication & authorization

---

## Service List

### 1. auth-service ✅ (Completed)

**Responsibilities:**

- Login
- Registration
- Refresh token
- JWT generation (RSA key pair)
- User credential validation
- User profile management

**Important Notes:**

- Does NOT handle authorization logic
- Only responsible for authentication
- JWT contains: `userId`, `userName`, `userEmail`, `userRole`, `phoneNumber`, account status flags
- Uses `token_type` claim to differentiate access vs refresh tokens

**Endpoints:**

| Method | Path                | Description      |
|--------|---------------------|------------------|
| POST   | /api/v1/auth/login    | User login       |
| POST   | /api/v1/auth/register | User registration|
| POST   | /api/v1/auth/refresh  | Refresh token    |

---

### 2. gateway-service ✅ (Completed)

**Responsibilities:**

- API Gateway (Spring Cloud Gateway)
- JWT validation (RSA public key verification)
- Role-based authorization
- Route requests to downstream services
- Propagate user context via X-headers
- Idempotency key enforcement with Redis deduplication
- Generate and propagate Correlation ID

**Authorization:**

| Path Prefix      | Allowed Roles |
|------------------|---------------|
| /api/v1/orders   | USER, ADMIN   |
| /api/v1/admin    | ADMIN         |

**Open Paths (no auth required):**
- `/api/v1/auth`

**Important Notes:**

- Gateway is the main security layer
- Internal services are private inside Kubernetes network
- Idempotency check via Redis `storeIfAbsent` — returns `DUPLICATE_REQUEST` error if key already exists
- Uses `GlobalFilter` with `Ordered` interface for filter priority

**Security Flow:**

```
Client → [JWT] → Gateway validates JWT → extracts claims → mutates headers → forwards to downstream
```

**Propagated Headers:**

- `X-User-Id`
- `X-User-Email`
- `X-User-Roles`
- `X-Correlation-Id` (UUID generated per request)

**Idempotency Flow:**

```
POST /api/v1/orders → Check X-Idempotency-Key header exists → Redis storeIfAbsent →
  → Already exists: Return DUPLICATE_REQUEST (409 Conflict)
  → New: Forward request downstream
```

---

### 3. order-service ✅ (Completed)

**Responsibilities:**

- Create order
- Store order in database (R2DBC PostgreSQL)
- Apply discount codes (strategy pattern)
- Publish `STOCK_RESERVE_REQUESTED` event
- Return `transactionId` to client (optimistic — doesn't wait for stock)
- Consume status update events to sync own DB
- Event deduplication via Redis (eventId as key)
- Record order event history to `order_ledger` table (audit trail)

**Order Status:**

| Status          |
|-----------------|
| PENDING         |
| WAITING_PAYMENT |
| PAID            |
| FAILED          |
| COMPLETED       |
| REFUNDED        |

**Endpoints:**

| Method | Path                              | Description         |
|--------|-----------------------------------|---------------------|
| POST   | /api/v1/orders                    | Create order        |
| GET    | /api/v1/orders/status/{transactionId} | Get order status |
| GET    | /api/v1/orders                    | Get user's orders   |

**Flow:**

```
Client → Create Order → Extract JWT claims + Get product prices from inventory-service (parallel) →
  → Validate products → Calculate total → Apply discount → Save DB (PENDING) →
  → Record order ledger → Publish STOCK_RESERVE_REQUESTED → Return transactionId
```

**Kafka Producer:**

| Topic                    | Key            | Trigger        |
|--------------------------|----------------|----------------|
| stock-reserve-requested  | UUID (eventId) | Order created  |

**Kafka Consumer (OrderEventReceiver):**

| Topic                    | Action                      |
|--------------------------|-----------------------------|
| order-completed          | Update status → COMPLETED   |
| order-failed             | Update status → FAILED      |
| stock-reserve-completed  | Update status → WAITING_PAYMENT |
| payment-completed        | Update status → PAID        |
| order-refund-completed   | Update status → REFUNDED    |

**Event Deduplication:**

```
Receive Kafka event → Redis storeIfAbsent(eventId) →
  → Already exists: Skip (log duplicate)
  → New: Process event → retry(3) on failure
  → Always: acknowledge offset
```

**Order Ledger (Event History):**

Every order status change is recorded in the `order_ledger` table as an immutable audit trail.

| Column               | Description                          |
|----------------------|--------------------------------------|
| order_transaction_id | Links to the order's transactionId   |
| correlation_id       | Distributed tracing ID               |
| event_type           | Status at time of recording (PENDING, WAITING_PAYMENT, etc.) |
| created_date         | Timestamp of the event               |

**Recorded on:**
- Order creation (PENDING)
- Every Kafka event consumption that updates order status (WAITING_PAYMENT, PAID, COMPLETED, FAILED, REFUNDED)

**Important Notes:**

- User information extracted from JWT (forwarded by gateway)
- Correlation ID stored in order entity
- Does NOT wait for stock reservation (optimistic)
- Discount system uses Strategy pattern with `Map<String, DiscountStrategy>` injection
- Supports PERCENTAGE and FIXED discount types
- Calls inventory-service via WebClient (`POST /api/v1/products/list`) to get product prices at order time
- Uses `TransactionalOperator` to save order + order items atomically
- On create order error: sets order status to FAILED in DB before propagating error

---

### 4. inventory-service ✅ (Completed)

**Responsibilities:**

- Reserve stock
- Release stock (compensation)
- Deduct stock (confirm sold)
- Handle out-of-stock scenario (produce OUT_OF_STOCK event)
- Track stock ledger (immutable audit trail)
- Event deduplication via Redis (eventId as key)
- Expose REST endpoint for product price lookup (used by order-service)

**Database Tables:**

| Table             | Purpose                                         |
|-------------------|-------------------------------------------------|
| products          | Product catalog with available/reserved/sold qty |
| stock_reservation | Tracks per-order stock reservations with status  |
| stock_ledger      | Immutable audit trail of all stock events        |

**Endpoints:**

| Method | Path                    | Description                          |
|--------|-------------------------|--------------------------------------|
| POST   | /api/v1/products/list   | Get products by IDs (price lookup)   |

**Reservation Status:**

| Status      | Description                              |
|-------------|------------------------------------------|
| RESERVED    | Stock reserved for an order              |
| OUT_OF_STOCK| Insufficient stock                       |
| RELEASED    | Reservation cancelled (compensation)     |
| DEDUCTED    | Reservation confirmed as sold            |

**Kafka Consumer (StockCommandReceiver):**

| Topic                    | Action                                    |
|--------------------------|-------------------------------------------|
| stock-reserve-requested  | Reserve stock → update product qty → record ledger → produce STOCK_RESERVE_COMPLETED |
| release-stock            | Update reservation status → release product qty → record ledger |
| deduct-stock             | Update reservation status → deduct product qty → record ledger |

**Kafka Producer (StockEventProducer):**

| Topic                    | Condition          |
|--------------------------|--------------------|
| out-of-stock             | Stock insufficient |
| stock-reserve-completed  | Stock reserved OK  |

**Event Deduplication:**

```
Receive Kafka event → Redis storeIfAbsent(eventId) →
  → Already exists: Skip (log duplicate)
  → New: Process event → retry(3) on failure
  → Always: acknowledge offset
```

**Stock Reserve Flow:**

```
Consume STOCK_RESERVE_REQUESTED →
  1. Create StockReservation records (status=RESERVED)
  2. Update Product: availableQty -= qty, reservedQty += qty
     → If insufficient stock or product inactive/deleted: throw OUT_OF_STOCK error
  3. Record StockLedger entry
  4. Produce STOCK_RESERVE_COMPLETED event

On OUT_OF_STOCK error:
  1. Update reservations status → OUT_OF_STOCK
  2. Record StockLedger entry
  3. Produce OUT_OF_STOCK event (with failureCode + failureMessage)
```

**Release Stock Flow (Compensation):**

```
Consume RELEASE_STOCK →
  1. Find reservations by transactionId → set status=RELEASED
  2. Update Product: availableQty += qty, reservedQty -= qty
  3. Record StockLedger entry
```

**Deduct Stock Flow (Confirm Sold):**

```
Consume DEDUCT_STOCK →
  1. Find reservations by transactionId → set status=DEDUCTED
  2. Update Product: reservedQty -= qty, soldQty += qty
  3. Record StockLedger entry
```

**Out of Stock Flow:**

```
Consume STOCK_RESERVE_REQUESTED → Product check fails (qty < requested OR !isActive OR isDeleted) →
  1. Find reservations by transactionId → set status=OUT_OF_STOCK
  2. Record StockLedger entry
  3. Produce OUT_OF_STOCK event with failureCode/failureMessage
```

**Important Notes:**

- Inventory reservation is part of saga pattern
- Each product tracks: available, reserved, sold quantities
- Stock ledger provides full audit trail of every stock movement
- Uses reactor-kafka (KafkaReceiver/KafkaSender) for reactive Kafka
- REST endpoint (`/api/v1/products/list`) called by order-service during order creation to get product prices
- Validates JWT on REST endpoint via common-lib JwtService

---

### 5. payment-service ✅ (Completed)

**Responsibilities:**

- Accept payment request with `transactionId` + payment method
- Support 4 dummy payment methods (DEBIT_CARD, CREDIT_CARD, BCA_VA, BNI_VA)
- Handle payment webhook callback (unified endpoint for payment + refund callbacks)
- Refund payment (consume REFUND_REQUESTED → call third-party → wait webhook)
- Publish payment events
- Validate order status before creating payment (rejects if PENDING, PAID, COMPLETED, FAILED, REFUNDED)
- Track payment ledger (immutable audit trail)
- Event deduplication via Redis (eventId as key)
- Calls order-service via WebClient to get order status before payment

**Payment Status:**

| Status       | Description                         |
|--------------|-------------------------------------|
| PENDING      | Payment created, awaiting callback  |
| SUCCESS      | Payment callback success            |
| FAILED       | Payment callback failed             |
| REFUNDED     | Refund callback success             |
| REFUND_FAILED| Refund callback failed              |

**Endpoints:**

| Method | Path                            | Description                              |
|--------|---------------------------------|------------------------------------------|
| POST   | /api/v1/payments                | Create payment (returns payment URL)     |
| POST   | /api/v1/payments/callback       | Webhook callback (payment + refund)      |
| GET    | /api/v1/payments                | Get user's payments                      |

**Create Payment Response:**

```json
{
  "transactionId": "order-123",
  "amount": 100000.0,
  "paymentMethod": "BCA_VA",
  "urlPayment": "http://bca.com/payment/xxx"
}
```

**Webhook Callback Request Body:**

```json
{
  "transactionId": "order-123",
  "paymentStatus": "PAYMENT_SUCCESS",
  "failureCode": null,
  "failureMessage": null
}
```

**Supported `paymentStatus` values in callback:**
- `PAYMENT_SUCCESS` → payment completed
- `PAYMENT_FAILED` → payment failed (with failureCode/failureMessage)
- `REFUND_SUCCESS` → refund completed
- `REFUND_FAILED` → refund failed (with failureCode/failureMessage)

**Demo Flow (Postman):**
- Create payment → get `urlPayment` in response
- Manually call webhook callback endpoint with `PAYMENT_SUCCESS` or `PAYMENT_FAILED` to simulate provider callback
- Same for refund: fulfillment triggers refund via Kafka → payment-service calls third-party → simulate callback with `REFUND_SUCCESS` or `REFUND_FAILED`

**Kafka Producer:**

| Topic                  | Condition              |
|------------------------|------------------------|
| payment-completed      | Callback PAYMENT_SUCCESS |
| payment-failed         | Callback PAYMENT_FAILED  |
| order-refund-completed | Callback REFUND_SUCCESS  |
| order-refund-failed    | Callback REFUND_FAILED   |

**Kafka Consumer:**

| Topic            | Action                                                |
|------------------|-------------------------------------------------------|
| refund-requested | Process refund → call third-party → wait webhook callback |

---

### 6. fulfillment-service (Planned)

**Responsibilities:**

- Saga coordinator for all outcomes
- Decide final order result
- Handle all compensation logic
- (Future) Call third-party API for actual fulfillment (e.g. shipping, purchasing from supplier)

**Kafka Consumer:**

| Topic              | Source            |
|--------------------|-------------------|
| payment-completed  | payment-service   |
| payment-failed     | payment-service   |
| out-of-stock       | inventory-service |

**Saga Logic (current — coordinator only):**

| Condition                    | Actions                                           |
|------------------------------|---------------------------------------------------|
| Payment success              | Publish ORDER_COMPLETED + DEDUCT_STOCK            |
| Payment failed               | Publish ORDER_FAILED + RELEASE_STOCK              |
| Out of stock (no payment)    | Publish ORDER_FAILED                              |
| Out of stock (payment exists)| Publish REFUND_REQUESTED + ORDER_FAILED           |

**Saga Logic (future — with third-party fulfillment):**

| Condition                    | Actions                                              |
|------------------------------|------------------------------------------------------|
| Payment success              | Call third-party API to fulfill                      |
| Fulfillment success          | Publish ORDER_COMPLETED + DEDUCT_STOCK               |
| Fulfillment failed           | Publish REFUND_REQUESTED + ORDER_FAILED + RELEASE_STOCK |
| Payment failed               | Publish ORDER_FAILED + RELEASE_STOCK                 |
| Out of stock (no payment)    | Publish ORDER_FAILED                                 |
| Out of stock (payment exists)| Publish REFUND_REQUESTED + ORDER_FAILED              |

**Kafka Producer:**

| Topic             | Condition                                |
|-------------------|------------------------------------------|
| deduct-stock      | Payment success / Fulfillment success    |
| release-stock     | Payment failed / Fulfillment failed      |
| refund-requested  | Out of stock (paid) / Fulfillment failed |
| order-completed   | Payment success / Fulfillment success    |
| order-failed      | Payment failed / Out of stock / Fulfillment failed |

**Important Notes:**

- This is the single source of truth for saga decisions
- No scattered decision-making across services
- Currently acts as coordinator only — no external API calls
- Future: add third-party fulfillment (shipping, supplier purchase) which introduces real "fulfillment failed" scenario
- Fulfillment failed publishes ALL compensation events at once (REFUND_REQUESTED + ORDER_FAILED + RELEASE_STOCK) — no need to wait for refund to complete before releasing stock

---

### 7. common-lib ✅ (Completed)

**Shared across all services:**

- `ErrorCode` enum — centralized error codes with code, message, HTTP status
- `BusinessException` — custom exception thrown with ErrorCode
- `JwtService` — JWT claims extraction using RSA public key
- `KeyUtils` — RSA key loading (reactive, offloaded to boundedElastic)
- `RedisService` / `RedisServiceImpl` — idempotency helpers (isDuplicate, store, storeIfAbsent)
- `RedisConfig` — ReactiveRedisTemplate bean configuration

---

## Complete Flows

### Flow 1: Happy Path (Order Success)

```
1. Client → order-service: Create order → Extract JWT + Get prices from inventory-service (parallel) →
   Validate products → Calculate total → Apply discount → Save DB (PENDING) →
   Record ledger → Publish STOCK_RESERVE_REQUESTED → Return transactionId
2. inventory-service: Consumes STOCK_RESERVE_REQUESTED → Reserve stock ✅ → Publish STOCK_RESERVE_COMPLETED
3. order-service: Consumes STOCK_RESERVE_COMPLETED → Update status → WAITING_PAYMENT
4. Client → payment-service: Send transactionId + payment method → Return dummy callbacks
5. Client → payment callback success → payment-service: Publish PAYMENT_COMPLETED
6. order-service: Consumes PAYMENT_COMPLETED → Update status → PAID
7. fulfillment-service: Consumes PAYMENT_COMPLETED → Publish ORDER_COMPLETED + DEDUCT_STOCK
8. inventory-service: Consumes DEDUCT_STOCK → Convert reservation to sold ✅
9. order-service: Consumes ORDER_COMPLETED → Update status → COMPLETED ✅
```

### Flow 2: Payment Failed

```
1. Client → order-service: Create order → Save DB (PENDING) → Publish STOCK_RESERVE_REQUESTED → Return transactionId
2. inventory-service: Consumes STOCK_RESERVE_REQUESTED → Reserve stock ✅ → Publish STOCK_RESERVE_COMPLETED
3. order-service: Consumes STOCK_RESERVE_COMPLETED → Update status → WAITING_PAYMENT
4. Client → payment-service: Send transactionId + payment method → Return dummy callbacks
5. Client → payment callback failed → payment-service: Publish PAYMENT_FAILED
6. fulfillment-service: Consumes PAYMENT_FAILED → Publish ORDER_FAILED + RELEASE_STOCK
7. inventory-service: Consumes RELEASE_STOCK → Cancel reservation ✅
8. order-service: Consumes ORDER_FAILED → Update status → FAILED ✅
```

### Flow 3: Out of Stock (No Payment Yet)

```
1. Client → order-service: Create order → Save DB (PENDING) → Publish STOCK_RESERVE_REQUESTED → Return transactionId
2. inventory-service: Consumes STOCK_RESERVE_REQUESTED → Stock insufficient → Publish OUT_OF_STOCK
3. fulfillment-service: Consumes OUT_OF_STOCK → No payment exists → Publish ORDER_FAILED
4. order-service: Consumes ORDER_FAILED → Update status → FAILED ✅
```

### Flow 4: Out of Stock (Payment Already Made)

```
1. Client → order-service: Create order → Save DB (PENDING) → Publish STOCK_RESERVE_REQUESTED → Return transactionId
2. Client → payment-service: Send transactionId + payment method → callback success → Publish PAYMENT_COMPLETED
3. inventory-service: Consumes STOCK_RESERVE_REQUESTED → Stock insufficient → Publish OUT_OF_STOCK
4. fulfillment-service: Consumes OUT_OF_STOCK → Payment exists → Publish REFUND_REQUESTED + ORDER_FAILED
5. payment-service: Consumes REFUND_REQUESTED → Process refund → Publish ORDER_REFUND_COMPLETED
6. order-service: Consumes ORDER_FAILED → Update status → FAILED
7. order-service: Consumes ORDER_REFUND_COMPLETED → Update status → REFUNDED ✅
```

### Flow 5: Fulfillment Failed (Future — with third-party API)

```
1. Client → order-service: Create order → Save DB (PENDING) → Publish STOCK_RESERVE_REQUESTED → Return transactionId
2. inventory-service: Consumes STOCK_RESERVE_REQUESTED → Reserve stock ✅ → Publish STOCK_RESERVE_COMPLETED
3. order-service: Consumes STOCK_RESERVE_COMPLETED → Update status → WAITING_PAYMENT
4. Client → payment-service: Send transactionId + payment method → callback success → Publish PAYMENT_COMPLETED
5. order-service: Consumes PAYMENT_COMPLETED → Update status → PAID
6. fulfillment-service: Consumes PAYMENT_COMPLETED → Call third-party API → Fails ❌ → Publish REFUND_REQUESTED + ORDER_FAILED + RELEASE_STOCK
7. inventory-service: Consumes RELEASE_STOCK → Cancel reservation ✅
8. order-service: Consumes ORDER_FAILED → Update status → FAILED
9. payment-service: Consumes REFUND_REQUESTED → Call third-party refund → Webhook callback REFUND_SUCCESS → Publish ORDER_REFUND_COMPLETED
10. order-service: Consumes ORDER_REFUND_COMPLETED → Update status → REFUNDED ✅
```

### Flow 6: Refund Failed (Future — DLQ/Retry)

```
1. (Continues from any refund flow above)
2. payment-service: Consumes REFUND_REQUESTED → Call third-party refund → Webhook callback REFUND_FAILED → Publish ORDER_REFUND_FAILED
3. (Future: DLQ handling, retry mechanism, or manual intervention)
4. Order remains in FAILED status (does not transition to REFUNDED)
```

---

## Infrastructure Components

### Redis

**Responsibilities:**

- Gateway idempotency (X-Idempotency-Key deduplication)
- Kafka event deduplication (eventId as key)
- TTL: 24 hours

**Gateway Deduplication Flow:**

```
Request arrives → Extract X-Idempotency-Key → Redis storeIfAbsent →
  → Key exists: Return 409 DUPLICATE_REQUEST
  → Key new: Forward request
```

**Event Deduplication Flow:**

```
Receive Kafka event → Redis storeIfAbsent(eventId) →
  → Key exists: Skip processing
  → Key new: Process event
```

---

### Kafka Topics

| Topic                    | Producer            | Consumer            |
|--------------------------|---------------------|---------------------|
| stock-reserve-requested  | order-service       | inventory-service   |
| stock-reserve-completed  | inventory-service   | order-service       |
| deduct-stock             | fulfillment-service | inventory-service   |
| release-stock            | fulfillment-service | inventory-service   |
| out-of-stock             | inventory-service   | fulfillment-service |
| payment-completed        | payment-service     | order-service, fulfillment-service |
| payment-failed           | payment-service     | fulfillment-service |
| refund-requested         | fulfillment-service | payment-service     |
| order-refund-completed   | payment-service     | order-service       |
| order-refund-failed      | payment-service     | (future: DLQ/retry) |
| order-completed          | fulfillment-service | order-service       |
| order-failed             | fulfillment-service | order-service       |

**Note:** order-service is both a producer (`stock-reserve-requested`) and consumer (`order-completed`, `order-failed`, `stock-reserve-completed`, `payment-completed`, `order-refund-completed`) because each service owns its own DB — status updates must flow back via events.

---

### Correlation ID

**Purpose:** Distributed tracing across services

**Header:** `X-Correlation-Id`

**Generated by:** Gateway (UUID per request)

**Must be propagated to:**

- HTTP headers (downstream services)
- Kafka event payloads
- Logs
- Order entity (`correlationId` field)

---

### JWT Architecture

**Strategy:**

1. auth-service signs JWT with RSA private key
2. Gateway validates JWT with RSA public key (from common-lib)
3. Gateway extracts claims and propagates as X-headers
4. Downstream services can also verify JWT via common-lib's JwtService

**Key Storage:**

- Private key: `auth-service/src/main/resources/keys/private_key.pem`
- Public key: `common-lib/src/main/resources/keys/public_key.pem`

---

## Kubernetes Architecture (Phase 4)

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

---

## Project Structure (Actual)

```
reactive-order-microservice/
├── common-lib/                     # Shared library
│   └── src/main/java/com/MSyamsandiYW/common/
│       ├── exception/ (BusinessException, ErrorCode, ErrorResponse, ValidationError)
│       ├── jwt/ (JwtService, KeyUtils)
│       └── redis/ (RedisConfig, RedisService, impl/RedisServiceImpl)
├── auth-service/                   # Authentication service
│   └── src/main/java/com/MSyamsandiYW/auth_service/
│       ├── auth/ (controller, service, impl, request/, response/)
│       ├── config/
│       ├── handler/
│       ├── security/
│       ├── user/
│       └── validation/
├── gateway-service/                # API Gateway
│   └── src/main/java/com/MSyamsandiYW/gateway_service/
│       ├── config/ (JwtConfig, RedisServiceConfig)
│       ├── handler/ (ApplicationExceptionHandler)
│       └── security/ (JwtAuthFilter, RouteValidator, TokenValidator, SecurityConfig)
├── order-service/                  # Order processing
│   └── src/main/java/com/MSyamsandiYW/order_service/
│       ├── config/ (JwtServiceConfig, KafkaConfig, R2dbcConfig, RedisServiceConfig, WebClientConfig)
│       ├── discount/ (Discount, DiscountRepository, DiscountService, DiscountStrategy, impl/)
│       ├── handler/ (ApplicationExceptionHandler)
│       ├── kafka/ (OrderEventReceiver, OrderEventHandler, OrderEventProducer, request/)
│       ├── order/ (Order, OrderController, OrderService, OrderRepository, impl/, request/, response/)
│       ├── order_item/ (OrderItem, OrderItemRepository, request/)
│       ├── order_ledger/ (OrderLedger, OrderLedgerRepository, OrderLedgerService, impl/)
│       ├── properties/ (AppConstant, AppProperties)
│       └── service/ (InventoryServiceClient)
├── inventory-service/              # Stock management
│   └── src/main/java/com/MSyamsandiYW/inventory_service/
│       ├── config/ (JwtServiceConfig, KafkaConfig, R2dbcConfig, RedisServiceConfig)
│       ├── handler/ (ApplicationExceptionHandler)
│       ├── kafka/ (StockCommandReceiver, StockCommandHandler, StockEventProducer)
│       ├── kafka/event/ (StockCommand, StockItem, OrderStatusEvent)
│       ├── product/ (Product, ProductController, ProductRepository, ProductService, impl/, request/, response/)
│       ├── stock_reservation/ (StockReservation, StockReservationRepository, StockReservationService, impl/)
│       ├── stock_ledger/ (StockLedger, StockLedgerRepository, StockLedgerService, impl/)
│       └── properties/ (AppConstant)
├── payment-service/                # Payment processing
│   └── src/main/java/com/MSyamsandiYW/payment_service/
│       ├── config/ (JwtServiceConfig, KafkaConfig, R2dbcConfig, RedisServiceConfig, WebClientConfig)
│       ├── kafka/ (PaymentCommandReceiver, PaymentCommandHandler, PaymentCommandProducer)
│       ├── kafka/event/ (PaymentCommand, PaymentEventPayload)
│       ├── payment/ (Payment, PaymentService, PaymentRepository, impl/, request/, response/)
│       ├── payment_ledger/ (PaymentLedger, PaymentLedgerRepository, PaymentLedgerService, impl/)
│       ├── properties/ (AppConstant, AppProperties)
│       └── service/ (OrderServiceClient)
├── fulfillment-service/            # (Planned)
├── docker-compose.yml              # Infrastructure (Kafka, Zookeeper, Redis)
└── pom.xml                         # Parent POM (modules: common-lib, auth-service, gateway-service, order-service)
```

---

## Development Phases

### Phase 1 — MVP ✅ (In Progress)

- [x] JWT auth (RSA key pair, access + refresh tokens)
- [x] Gateway authorization (role-based, JWT validation)
- [x] Gateway idempotency (Redis deduplication)
- [x] Create order (with discount system)
- [x] Kafka event producer/consumer (order-service)
- [x] Event deduplication (Redis, eventId key)
- [x] common-lib (shared JWT, exceptions, Redis)
- [x] inventory-service (reserve, release, deduct, out-of-stock handling, product price lookup API)
- [x] inventory-service infrastructure (docker-compose, application.yaml, R2dbcConfig, .env.example)
- [x] payment-service (4 dummy payment methods, webhook callback, refund flow, payment ledger)
- [ ] fulfillment-service (saga coordinator)
- **Docker Compose** for local development

### Phase 2 — Reliability

- [x] Redis idempotency (gateway + event consumer)
- [ ] Retry with backoff
- [ ] Dead Letter Queue (DLQ)
- [ ] Outbox pattern
- [ ] Full compensation flow testing

### Phase 3 — Observability

- [x] Correlation ID generation (gateway)
- [ ] Correlation ID in all logs
- [ ] Distributed tracing (OpenTelemetry)
- [ ] Metrics
- [ ] Centralized logging

### Phase 4 — Kubernetes

- [ ] Kubernetes deployment manifests
- [ ] ConfigMap & Secret
- [ ] Horizontal scaling
- [ ] Health checks / readiness probes

---

## Enterprise Concepts Used

| Concept                    | Status |
|----------------------------|--------|
| JWT Authentication (RSA)   | ✅     |
| Role-based Authorization   | ✅     |
| API Gateway                | ✅     |
| Reactive Programming       | ✅     |
| Saga Pattern               | 🔄 (planned in fulfillment-service) |
| Compensation Transaction   | 🔄     |
| Event-Driven Architecture  | ✅     |
| Kafka Messaging            | ✅     |
| Idempotency (Redis)        | ✅     |
| Event Deduplication        | ✅     |
| Distributed Tracing        | 🔄     |
| Correlation ID             | ✅     |
| Kubernetes                 | 🔄 (Phase 4) |
| Docker                     | ✅     |
| Strategy Pattern           | ✅ (discount) |
| Event Sourcing (Ledger)    | ✅ (order_ledger) |
| Database per Service       | ✅     |

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

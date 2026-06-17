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
- Saga pattern (orchestrator-service as coordinator)
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

| Status          | Trigger                                          |
|-----------------|--------------------------------------------------|
| PENDING         | Order created                                    |
| WAITING_PAYMENT | Stock reserved                                   |
| PAID            | Payment completed                                |
| OUT_OF_STOCK    | Stock insufficient                               |
| EXPIRED         | Scheduler: order expiry (user never paid)        |
| COMPLETED       | Fulfillment success                              |
| REFUNDED        | Refund completed                                 |
| REFUND_FAILED   | Refund callback failed                           |

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
| stock-reserve-completed  | Update status → WAITING_PAYMENT |
| out-of-stock             | Update status → OUT_OF_STOCK |
| payment-completed        | Update status → PAID        |
| order-refund-completed   | Update status → REFUNDED    |
| order-refund-failed      | Update status → REFUND_FAILED |
| order-expired            | Update status → EXPIRED     |

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
- Every Kafka event consumption that updates order status (WAITING_PAYMENT, PAID, COMPLETED, OUT_OF_STOCK, EXPIRED, REFUNDED, REFUND_FAILED)

**Important Notes:**

- User information extracted from JWT (forwarded by gateway)
- Correlation ID stored in order entity
- Does NOT wait for stock reservation (optimistic)
- Discount system uses Strategy pattern with `Map<String, DiscountStrategy>` injection
- Supports PERCENTAGE and FIXED discount types
- Calls inventory-service via WebClient (`POST /api/v1/products/list`) to get product prices at order time
- Uses `TransactionalOperator` to save order + order items atomically
- On create order error: sets order status to FAILED in DB before propagating error
- Order DB columns include `failureCode` and `failureMessage` for failure context

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
- Validate order status before creating payment (rejects if PAID, COMPLETED, REFUNDED, OUT_OF_STOCK, EXPIRED, REFUND_FAILED)
- Cancel existing PENDING payment when user switches payment method (cancel + create pattern)
- Track payment ledger (immutable audit trail)
- Event deduplication via Redis (eventId as key)
- Calls order-service via WebClient to get order status before payment

**Payment Status:**

| Status       | Description                                  |
|--------------|----------------------------------------------|
| PENDING      | Payment created, awaiting callback           |
| CANCELLED    | Superseded by new payment attempt (user switched method) |
| SUCCESS      | Payment callback success                     |
| FAILED       | Payment callback failed                      |
| REFUNDED     | Refund callback success                      |
| REFUND_FAILED| Refund callback failed                       |

**Endpoints:**

| Method | Path                                | Description                              |
|--------|-------------------------------------|------------------------------------------|
| POST   | /api/v1/payments                    | Create payment (returns payment URL)     |
| POST   | /api/v1/payments/webhook/callback   | Webhook callback (payment + refund)      |
| GET    | /api/v1/payments/list               | Get user's payments                      |

**Payment Method Switching (Cancel + Create Pattern):**

- User can retry payment with different method as long as order is not expired
- Before creating new payment, cancel any existing PENDING payment for the same `transactionId`
- Existing PENDING payment → marked as CANCELLED → new payment created
- No PENDING payment → create new payment directly
- Prevents double-charge while allowing payment method switching

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
- Same for refund: orchestrator triggers refund via Kafka → payment-service calls third-party → simulate callback with `REFUND_SUCCESS` or `REFUND_FAILED`

**Kafka Producer:**

| Topic                  | Condition              |
|------------------------|------------------------|
| payment-initiated      | Payment created (PENDING) |
| payment-completed      | Callback PAYMENT_SUCCESS |
| payment-failed         | Callback PAYMENT_FAILED  |
| order-refund-completed | Callback REFUND_SUCCESS  |
| order-refund-failed    | Callback REFUND_FAILED   |

**Kafka Consumer:**

| Topic            | Action                                                |
|------------------|-------------------------------------------------------|
| refund-requested | Process refund → call third-party → wait webhook callback |

---

### 6. orchestrator-service ✅ (Completed)

**Responsibilities:**

- Saga coordinator for all outcomes
- Decide final order result
- Handle all compensation logic
- Track saga state per transaction (stateful coordinator)
- Handle events arriving in any order (race condition handling)
- Payment timeout — auto-expire orders if unpaid after configurable duration (scheduler)

**Database Tables:**

| Table       | Purpose                                    |
|-------------|--------------------------------------------|
| saga_state  | Tracks saga progress per transactionId     |

**Saga State Table:**

| Column          | Type        | Description                                      |
|-----------------|-------------|--------------------------------------------------|
| id              | UUID        | Primary key (gen_random_uuid())                  |
| transaction_id  | VARCHAR     | Order identifier                                 |
| correlation_id  | VARCHAR     | Distributed tracing ID                           |
| stock_status    | VARCHAR     | NULL → RESERVED / OUT_OF_STOCK                   |
| payment_status  | VARCHAR     | NULL → INITIATED / PAID / FAILED                 |
| saga_status     | VARCHAR     | IN_PROGRESS / COMPLETED / COMPENSATING / FAILED  |
| created_by      | VARCHAR     | Service that created the record                  |
| updated_by      | VARCHAR     | Service that last updated                        |
| created_date    | TIMESTAMPTZ | Saga creation time                               |
| last_modified_date | TIMESTAMPTZ | Last event received                           |

**Kafka Consumer:**

| Topic                   | Source            |
|-------------------------|-------------------|
| stock-reserve-completed | inventory-service |
| payment-initiated       | payment-service   |
| payment-completed       | payment-service   |
| payment-failed          | payment-service   |
| out-of-stock            | inventory-service |
| order-refund-completed  | payment-service   |
| order-refund-failed     | payment-service   |

**Saga Decision Logic (handles events in any order):**

When `STOCK_RESERVE_COMPLETED` arrives:
| payment_status | Action                                              |
|----------------|-----------------------------------------------------|
| NULL           | Update stock_status=RESERVED, wait for payment      |
| INITIATED      | Update stock_status=RESERVED, wait for payment result |
| PAID           | Publish ORDER_COMPLETED + DEDUCT_STOCK → saga_status=COMPLETED |
| FAILED         | Update stock_status=RESERVED, wait for payment (user can retry) |

When `PAYMENT_INITIATED` arrives:
| saga_status    | Action                                              |
|----------------|-----------------------------------------------------|
| IN_PROGRESS    | Update payment_status=INITIATED, wait for callback  |

When `PAYMENT_COMPLETED` arrives:
| stock_status   | Action                                              |
|----------------|-----------------------------------------------------|
| NULL           | Update payment_status=PAID, wait for stock result   |
| RESERVED       | Publish ORDER_COMPLETED + DEDUCT_STOCK → saga_status=COMPLETED |
| OUT_OF_STOCK   | Publish REFUND_REQUESTED → saga_status=COMPENSATING |

When `PAYMENT_FAILED` arrives:
| stock_status   | Action                                              |
|----------------|-----------------------------------------------------|
| ANY            | Log it, do NOT fail the order (user can retry with different method) |

When `OUT_OF_STOCK` arrives:
| payment_status | Action                                              |
|----------------|-----------------------------------------------------|
| NULL           | Update stock_status=OUT_OF_STOCK → saga_status=FAILED |
| INITIATED      | Update stock_status=OUT_OF_STOCK, wait for payment result |
| PAID           | Publish REFUND_REQUESTED → saga_status=COMPENSATING |
| FAILED         | Update stock_status=OUT_OF_STOCK → saga_status=FAILED |

**Payment Timeout / Order Expiry (Scheduler):**

Two separate timeout scenarios:

```
Find saga_state WHERE stock_status=RESERVED AND saga_status=IN_PROGRESS
  AND created_date < (now - order_expiry_duration) →
  Publish ORDER_EXPIRED + RELEASE_STOCK → saga_status=FAILED
```

- Order expiry: configurable (e.g. 1 hour)
- Prevents stock being locked indefinitely
- Payment failure does NOT trigger order expiry — only the scheduler does

**Refund Result Handling:**

When `ORDER_REFUND_COMPLETED` arrives:
| saga_status    | Action                                              |
|----------------|-----------------------------------------------------|
| COMPENSATING   | Update saga_status=COMPLETED (compensation done)    |

When `ORDER_REFUND_FAILED` arrives:
| saga_status    | Action                                              |
|----------------|-----------------------------------------------------|
| COMPENSATING   | Update saga_status=FAILED (needs manual intervention) |

**Kafka Producer:**

| Topic             | Condition                                         |
|-------------------|---------------------------------------------------|
| deduct-stock      | Payment success + stock reserved                  |
| release-stock     | Order expired + stock reserved                    |
| refund-requested  | Out of stock + payment already paid               |
| order-completed   | Payment success + stock reserved                  |
| order-expired     | Scheduler: order expiry                           |

**Race Condition Handling: Scheduler vs Late Payment**

Scenario: Scheduler fires `PAYMENT_TIMEOUT` at the exact same time payment callback arrives with `PAYMENT_COMPLETED`.

Solution: **Conditional DB update** (optimistic approach) — only update if state hasn't changed:

```sql
-- Scheduler tries to mark as FAILED
UPDATE saga_state 
SET saga_status = 'FAILED', payment_status = 'TIMEOUT'
WHERE transaction_id = ? 
  AND saga_status = 'IN_PROGRESS'
  AND payment_status = 'INITIATED';

-- Payment completed handler tries to mark as COMPLETED
UPDATE saga_state
SET saga_status = 'COMPLETED', payment_status = 'PAID'
WHERE transaction_id = ?
  AND saga_status = 'IN_PROGRESS'
  AND stock_status = 'RESERVED';
```

If `rowsAffected = 0` → another process already transitioned the state → skip publishing events.

In reactive code:
```java
return sagaRepository.updateStatusIfInProgress(transactionId, newStatus)
    .filter(rowsUpdated -> rowsUpdated > 0)
    .flatMap(__ -> publishEvents(...))
    .then();
```

Whoever commits first wins. The loser sees 0 rows and does nothing. No duplicate events.

**Payment Expiry Scheduler:**

Runs every 30 minutes (`0 */30 * * * *`). Configurable expiry via `app.payment-expiry-seconds` (default: 3600s).

Handles 3 categories of expired IN_PROGRESS sagas:

| Condition                              | Action                                         |
|----------------------------------------|------------------------------------------------|
| stock=RESERVED, payment≠PAID           | FAILED + ORDER_EXPIRED + RELEASE_STOCK         |
| stock≠RESERVED, payment=PAID           | COMPENSATING + ORDER_EXPIRED + REFUND_REQUESTED|
| stock≠RESERVED, payment≠PAID           | FAILED + ORDER_EXPIRED                         |

**Important Notes:**

- This is the single source of truth for saga decisions
- No scattered decision-making across services
- Stateful — tracks what events have been received per transaction
- Handles race conditions — events can arrive in any order
- Each event handler checks current state before making decisions
- ALL event handlers use conditional update (check saga_status = IN_PROGRESS before transition)
- Uses R2DBC for reactive DB access (same pattern as other services)
- Event deduplication via Redis (same pattern as other services)
- Saga state created when first event arrives for a transactionId
- Payment expiry scheduler prevents infinite stock reservation

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
3. orchestrator-service: Consumes STOCK_RESERVE_COMPLETED → Create/update saga_state (stock_status=RESERVED, payment_status=NULL) → Wait
4. order-service: Consumes STOCK_RESERVE_COMPLETED → Update status → WAITING_PAYMENT
5. Client → payment-service: Send transactionId + payment method → Return dummy callbacks
6. Client → payment callback success → payment-service: Publish PAYMENT_COMPLETED
7. orchestrator-service: Consumes PAYMENT_COMPLETED → Update saga_state (payment_status=PAID) → stock_status=RESERVED → Publish ORDER_COMPLETED + DEDUCT_STOCK → saga_status=COMPLETED
8. order-service: Consumes PAYMENT_COMPLETED → Update status → PAID
9. inventory-service: Consumes DEDUCT_STOCK → Convert reservation to sold ✅
10. order-service: Consumes ORDER_COMPLETED → Update status → COMPLETED ✅
```

### Flow 2: Payment Failed (User Can Retry)

```
1. Client → order-service: Create order → Save DB (PENDING) → Publish STOCK_RESERVE_REQUESTED → Return transactionId
2. inventory-service: Consumes STOCK_RESERVE_REQUESTED → Reserve stock ✅ → Publish STOCK_RESERVE_COMPLETED
3. orchestrator-service: Consumes STOCK_RESERVE_COMPLETED → saga_state (stock_status=RESERVED) → Wait
4. order-service: Consumes STOCK_RESERVE_COMPLETED → Update status → WAITING_PAYMENT
5. Client → payment-service: Send transactionId + payment method → Return dummy callbacks
6. Client → payment callback failed → payment-service: Publish PAYMENT_FAILED
7. orchestrator-service: Consumes PAYMENT_FAILED → logs it, does NOT fail the order (user can retry)
8. Order stays WAITING_PAYMENT — user can pick another payment method
9. (If user never pays → eventually expires via scheduler → Flow 5a/5b)
```

### Flow 3: Out of Stock (No Payment Yet)

```
1. Client → order-service: Create order → Save DB (PENDING) → Publish STOCK_RESERVE_REQUESTED → Return transactionId
2. inventory-service: Consumes STOCK_RESERVE_REQUESTED → Stock insufficient → Publish OUT_OF_STOCK
3. orchestrator-service: Consumes OUT_OF_STOCK → saga_state (stock_status=OUT_OF_STOCK, payment_status=NULL) → No payment → Publish ORDER_FAILED → saga_status=FAILED
4. order-service: Consumes OUT_OF_STOCK → Update status → OUT_OF_STOCK ✅
```

### Flow 4: Out of Stock (Payment Already Made — Race Condition)

```
1. Client → order-service: Create order → Save DB (PENDING) → Publish STOCK_RESERVE_REQUESTED → Return transactionId
2. Client → payment-service: Send transactionId + payment method (allowed while PENDING) → callback success → Publish PAYMENT_COMPLETED
3. orchestrator-service: Consumes PAYMENT_COMPLETED → saga_state (payment_status=PAID, stock_status=NULL) → Wait for stock result
4. order-service: Consumes PAYMENT_COMPLETED → Update status → PAID
5. inventory-service: Consumes STOCK_RESERVE_REQUESTED → Stock insufficient → Publish OUT_OF_STOCK
6. orchestrator-service: Consumes OUT_OF_STOCK → saga_state (stock_status=OUT_OF_STOCK) → payment_status=PAID → Publish REFUND_REQUESTED + ORDER_FAILED → saga_status=COMPENSATING
7. payment-service: Consumes REFUND_REQUESTED → Process refund → Webhook callback → Publish ORDER_REFUND_COMPLETED
8. orchestrator-service: Consumes ORDER_REFUND_COMPLETED → Update saga_status → COMPLETED (compensation done)
9. order-service: Consumes OUT_OF_STOCK → Update status → OUT_OF_STOCK
10. order-service: Consumes ORDER_REFUND_COMPLETED → Update status → REFUNDED ✅
```

### Flow 5: Order Expired (Stock Reserved, User Never Paid Successfully)

```
1. Client → order-service: Create order → Save DB (PENDING) → Publish STOCK_RESERVE_REQUESTED → Return transactionId
2. inventory-service: Consumes STOCK_RESERVE_REQUESTED → Reserve stock ✅ → Publish STOCK_RESERVE_COMPLETED
3. orchestrator-service: Consumes STOCK_RESERVE_COMPLETED → saga_state (stock_status=RESERVED, payment_status=NULL)
4. order-service: Consumes STOCK_RESERVE_COMPLETED → Update status → WAITING_PAYMENT
5. (Client never pays or all payment attempts fail — order expiry timer expires)
6. orchestrator-service: Scheduler detects expired saga → Publish ORDER_EXPIRED + RELEASE_STOCK → saga_status=FAILED
7. inventory-service: Consumes RELEASE_STOCK → Cancel reservation ✅
8. order-service: Consumes ORDER_EXPIRED → Update status → EXPIRED ✅
```

### Flow 6: Refund Failed

```
1. (Continues from any refund flow above)
2. payment-service: Consumes REFUND_REQUESTED → Call third-party refund → Webhook callback REFUND_FAILED → Publish ORDER_REFUND_FAILED
3. orchestrator-service: Consumes ORDER_REFUND_FAILED → Update saga_status → FAILED (needs manual intervention)
4. order-service: Consumes ORDER_REFUND_FAILED → Update status → REFUND_FAILED
```

---

## Service Ports

| Service | Port |
|---------|------|
| gateway-service | 8080 (default) |
| auth-service | 8081 |
| order-service | 8082 |
| inventory-service | 8083 |
| payment-service | 8084 |
| orchestrator-service | 8085 |

## Database Ports

| Service | DB Name | Host Port |
|---------|---------|----------|
| auth-service | auth_service_db | 5432 |
| order-service | order_service_db | 5433 |
| inventory-service | inventory_service_db | 5434 |
| payment-service | payment_service_db | 5435 |
| orchestrator-service | orchestrator_service_db | 5436 |

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
| stock-reserve-completed  | inventory-service   | order-service, orchestrator-service |
| deduct-stock             | orchestrator-service | inventory-service   |
| release-stock            | orchestrator-service | inventory-service   |
| out-of-stock             | inventory-service   | orchestrator-service |
| payment-completed        | payment-service     | order-service, orchestrator-service |
| payment-failed           | payment-service     | orchestrator-service |
| payment-initiated        | payment-service     | orchestrator-service |
| refund-requested         | orchestrator-service | payment-service     |
| order-refund-completed   | payment-service     | order-service, orchestrator-service |
| order-refund-failed      | payment-service     | order-service, orchestrator-service |
| order-completed          | orchestrator-service | order-service       |
| order-expired            | orchestrator-service | order-service       |

**Note:** order-service is both a producer (`stock-reserve-requested`) and consumer (`order-completed`, `stock-reserve-completed`, `payment-completed`, `order-refund-completed`, `order-refund-failed`, `order-expired`) because each service owns its own DB — status updates must flow back via events.

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
├── orchestrator-service/           # Saga coordinator
│   └── src/main/java/com/MSyamsandiYW/orchestrator_service/
│       ├── config/ (KafkaConfig, R2dbcConfig, RedisServiceConfig, SchedulerConfig)
│       ├── kafka/ (OrchestrationCommandHandler, OrchestratorCommandReceiver, OrchestratorEventProducer)
│       ├── kafka/event/ (OrchestratorCommand, OrchestratorEventPayload)
│       ├── properties/ (AppConstant, AppProperties)
│       ├── saga_state/ (SagaState, SagaStateRepository, SagaStateService, impl/SagaStateServiceImpl)
│       └── scheduler/ (SchedulerController, SchedulerService, impl/SchedulerServiceImpl)
├── docker-compose.yml              # Infrastructure (Kafka, Zookeeper, Redis)
└── pom.xml                         # Parent POM (modules: common-lib, auth-service, gateway-service, order-service, payment-service)
```

---

## Development Phases (Job-Ready Priority Split)

---

## 🚨 **URGENT - Job Application Ready (Next 2-3 Weeks)**

### **Phase 1: MVP Foundation ✅ (Current - DONE)**
- [x] JWT auth, Gateway, Order creation, Kafka flow
- [x] Reactive microservices with saga pattern
- [x] Event-driven architecture
- [x] Database-per-service pattern

### **Phase 2: Portfolio Presentation (URGENT - Do This NOW)**
- [ ] **README.md with architecture diagram** (1-2 days)
- [ ] **API documentation with Postman collection** (1 day)
- [ ] **Docker Compose one-command setup** (1 day)
- [ ] **Basic unit tests (30% coverage minimum)** (2-3 days)
- [ ] **Demo video/screenshots** (1 day)
- [ ] **Clean up code comments and formatting** (1 day)

### **Phase 3: Interview Preparation (URGENT - Next Week)**
- [ ] **System design explanation script** (1 day)
- [ ] **Prepare for common questions** (saga pattern, microservices, Kafka) (1 day)
- [ ] **Performance testing with JMeter basics** (1 day)
- [ ] **Basic monitoring with Spring Actuator** (1 day)

---

## 💼 **JOB HUNTING - Apply Immediately After Phase 2+3**

**You can confidently apply for mid-level Java backend roles with:**
- Reactive microservices ✅
- Saga pattern ✅  
- Kafka event-driven architecture ✅
- JWT security ✅
- Docker containerization ✅
- Good documentation and tests ✅

---

## 🔄 **WHILE JOB HUNTING - Continuous Improvement (Next 1-2 Months)**

### **Phase 4: Production Reliability (Do While Interviewing)**
- [ ] Circuit breakers (Resilience4j) (3-4 days)
- [ ] Retry mechanisms with backoff (2 days)
- [ ] Rate limiting at gateway (2 days)
- [ ] Integration tests with TestContainers (3-4 days)
- [ ] Dead Letter Queue (DLQ) (2-3 days)

### **Phase 5: DevOps Basics (High Interview Value)**
- [ ] GitHub Actions CI/CD pipeline (2-3 days)
- [ ] SonarQube integration (1-2 days)
- [ ] Kubernetes deployment basics (3-4 days)
- [ ] AWS deployment (ECS/EKS) (4-5 days)

### **Phase 6: Observability (Nice to Have)**
- [ ] Distributed tracing (Zipkin/Jaeger) (2-3 days)
- [ ] Prometheus + Grafana metrics (3-4 days)
- [ ] Centralized logging (ELK Stack) (3-4 days)

---

## 🚀 **FUTURE - After Getting Job (6+ Months Later)**

### **Phase 7: Advanced Security**
- [ ] OAuth2/OIDC integration
- [ ] Vault secrets management
- [ ] Security scanning and penetration testing
- [ ] Compliance frameworks (SOC2, GDPR)

### **Phase 8: Enterprise Scaling**
- [ ] Multi-region deployment
- [ ] Advanced caching strategies
- [ ] Database sharding and optimization
- [ ] Auto-scaling and cost optimization

### **Phase 9: Advanced Patterns**
- [ ] Event sourcing and CQRS
- [ ] GraphQL API layer
- [ ] Machine learning integration
- [ ] Real-time analytics with Kafka Streams

### **Phase 10: Staff Engineer Level**
- [ ] Multi-tenant architecture
- [ ] Service mesh (Istio)
- [ ] Advanced monitoring and alerting
- [ ] Team leadership and mentoring

---

## 🎯 **IMMEDIATE ACTION PLAN (This Week)**

### **Day 1-2: Documentation** ⚡ HIGHEST PRIORITY
```
1. Create impressive README.md with:
   - Architecture diagram (use draw.io)
   - Quick start guide
   - API endpoints documentation
   - Technology stack highlights

2. Record demo video (5-10 minutes):
   - Show complete order flow
   - Explain saga pattern
   - Demonstrate compensation transactions
```

### **Day 3-4: Testing** ⚡ CRITICAL FOR INTERVIEWS
```
3. Add basic unit tests:
   - OrderService (create order logic)
   - JWT validation
   - Saga coordinator logic
   - Target: 30-40% coverage minimum

4. Add one integration test:
   - Complete order flow end-to-end
   - Use TestContainers for databases
```

### **Day 5-7: Polish & Prepare**
```
5. Add comprehensive comments:
   - JavaDoc for all public methods and classes
   - Inline comments for complex business logic (saga decisions, compensation flows)
   - Architecture decision comments (why saga pattern, why reactive)
   - Code examples in comments for complex flows
   - README code walkthrough with explanations

6. Interview preparation:
   - Practice explaining saga pattern
   - Prepare system design walkthrough
   - Know your trade-offs (eventual consistency, etc.)
```

## 🏆 **Job Market Assessment**

**After Phase 2+3 (2-3 weeks), you can target:**
- **Mid-level Java Backend** roles (99% ready)
- **Microservices Developer** positions
- **Event-driven Architecture** roles
- **Spring Boot/WebFlux** specialist roles

**Salary expectations (global market):**
- **Mid-level**: $60-90K USD (depending on location)
- **Senior-level** (after Phase 4-6): $90-130K USD
- **Staff-level** (after all phases): $130-180K USD

**Companies you can target NOW:**
- Fintech companies (Stripe, Square, PayPal)
- E-commerce platforms (Shopify, Amazon)
- Consulting firms (Thoughtworks, Accenture)
- Mid-size tech companies
- Startups needing microservices expertise

---

## Enterprise Concepts Used

| Concept                    | Status |
|----------------------------|--------|
| JWT Authentication (RSA)   | ✅     |
| Role-based Authorization   | ✅     |
| API Gateway                | ✅     |
| Reactive Programming       | ✅     |
| Saga Pattern               | ✅     |
| Compensation Transaction   | ✅     |
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

- Kafka Streams analytics module (real-time order metrics, dashboards)
- Outbox Pattern
- mTLS
- Service Mesh
- OpenTelemetry
- Circuit Breaker
- Rate Limiter
- Multi-tenancy
- CQRS / Event Sourcing
- gRPC internal communication

---

## **📋 Experience Positioning Strategy**

### **Your Real Experience Breakdown:**
- **7 months Contract**: Payment integration (Xendit) + IoT backend (Toyota locker system)
- **Portfolio Gap**: Built WhatsApp microservices (impressed XL interviewers)
- **XL Axiata**: Enterprise telecom microservices (saga pattern, high scale)
- **Current Gap**: Building reactive order microservices (advanced patterns)

### **Total Experience: 2+ Years with Premium Skills** ✅
```
Payment Systems + IoT/Hardware Integration + Enterprise Microservices + Advanced Portfolio
= Better than most 4-5 year developers who only know CRUD
```

### **Your Unique Skill Stack:**
1. **Payment Integration** (Xendit recurring payments) → Perfect for fintech!
2. **IoT/Hardware Backend** (Toyota locker system) → Rare integration skills
3. **Enterprise Microservices** (XL Axiata saga patterns) → Advanced distributed systems
4. **Multi-stack Experience** (.NET, Spring Boot, Outsystems) → Versatile developer

## **🎯 Job Application Target Range**

### **✅ APPLY TO (High Success Rate):**
- **2-3 years experience** roles (your actual range)
- **Payment/Fintech Backend Developer** (Xendit experience!)
- **IoT Backend Developer** (Toyota hardware integration)
- **Microservices Developer** (XL enterprise experience)
- **Spring Boot Developer** (2+ years)
- **Integration Developer** (payment + hardware APIs)

### **✅ DEFINITELY APPLY (Perfect Match):**
- **Fintech companies** (payment integration background)
- **E-commerce** (payment + order processing)
- **IoT/Hardware companies** (Toyota locker experience)
- **Telecom companies** (XL domain knowledge)
- **System integration** roles

### **🚫 STILL AVOID:**
- **5+ years senior** roles 
- **Lead/Architect** positions
- **Pure frontend** roles

## **🎯 Job Application Target Range**

### **✅ APPLY TO (High Success Rate):**
- **2-4 years experience** roles
- **Mid-level Java Backend Developer**
- **Microservices Developer** (any experience level)
- **Spring Boot Developer** (2-3 years)
- **Event-Driven Architecture** roles
- **Reactive Programming** specialist roles

### **✅ DEFINITELY APPLY (Your Sweet Spot):**
- **3+ years Java Backend** 
- **Microservices experience required**
- **Kafka/Event-driven** experience
- **Saga pattern** mentioned (rare skill!)
- **Telecom/Fintech** companies (your domain experience)

### **🚫 AVOID (Waste of Time):**
- **5+ years senior** roles (auto-reject)
- **Lead/Architect** positions 
- **Legacy monolith** companies
- **Non-microservices** roles

## **🏢 Target Company Types**

### **Tier 1: High Success Rate**
- **Fintech startups** (need saga patterns for payments)
- **E-commerce companies** (order processing expertise)
- **Consulting firms** (Thoughtworks, Accenture - love microservices)
- **Mid-size tech companies** (100-1000 employees)
- **Other telecom companies** (your domain expertise)

### **Tier 2: Worth Trying**
- **Unicorn startups** (Gojek, Tokopedia, etc.)
- **Banks with digital transformation**
- **Cloud-native companies**
- **SaaS platforms**

### **Tier 3: Long Shot (But Try)**
- **FAANG** (focus on L3/L4 roles)
- **Big tech** (Microsoft, Google Cloud)

## **💪 How to Position Your Experience**

### **Resume Positioning:**
```
**Java Backend Developer**
2+ years specializing in payment integration, IoT systems, and microservices

• XL Axiata: Built enterprise telecom microservices with saga patterns
• Chubb Insurance: Integrated Xendit recurring payment system with business stakeholders
• Toyota Astra: Enhanced IoT locker management backend with hardware integration
• Portfolio: Reactive order processing with event-driven architecture
• Tech Stack: Java, Spring Boot, .NET, Kafka, microservices, payment APIs, IoT
```

### **Interview Talking Points:**
1. **"I have payment integration experience from Chubb project with Xendit APIs"**
2. **"Toyota taught me IoT/hardware integration - connecting physical devices to backend"**
3. **"XL Axiata gave me enterprise microservices and saga pattern expertise"**
4. **"My portfolio demonstrates advanced reactive patterns most developers don't know"**
5. **"I've worked across domains: insurance, automotive, telecom - brings diverse perspective"**

### **Project Stories for Interviews:**

**Chubb Payment Integration:**
```
"Integrated Xendit recurring payment system for insurance premiums. 
Worked directly with business analysts to understand requirements, 
studied API documentation, and coordinated with Outsystems frontend team. 
Learned payment flow complexities and API design patterns."
```

**Toyota IoT System:**
```
"Enhanced backend for smart locker system connecting hardware devices. 
Worked with existing Spring Boot + Thymeleaf codebase, integrated with 
hardware libraries, built management dashboard. Learned IoT challenges 
like device connectivity, real-time status updates, hardware failures."
```

**XL Microservices:**
```
"Built enterprise-grade telecom microservices using saga patterns for 
distributed transactions. Handled high-scale billing systems with 
event-driven architecture. Learned compensation transactions and 
distributed system challenges."
```

### **Gap Explanation:**
```
"I strategically used gap periods to build advanced portfolio projects that 
impressed my next employer. My WhatsApp microservices got me hired at XL Axiata, 
and now my reactive saga implementation demonstrates enterprise-level skills."
```
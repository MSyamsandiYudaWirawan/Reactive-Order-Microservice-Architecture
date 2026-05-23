Reactive Order Microservice Architecture Notes
Tech Stack
Core Technologies
Spring WebFlux
Apache Kafka
Redis
Docker
Kubernetes
PostgreSQL
JWT Authentication
Reactive WebClient
Architecture Goals

This project focuses on:

Reactive microservices
Event-driven architecture
Saga pattern
Compensation transaction
Idempotency
Distributed tracing
Cloud-native deployment
JWT authentication & authorization
Service List
1. auth-service
   Responsibilities
   Login
   Refresh token
   JWT generation
   User credential validation
   Important Notes
   Does NOT handle authorization logic
   Only responsible for authentication
   JWT contains:
   userId
   role
   email
   Example Endpoints
   POST /auth/login
   POST /auth/refresh
2. gateway-service
   Responsibilities
   API Gateway
   JWT validation
   Authorization
   Route requests
   Generate correlationId
   Propagate JWT downstream
   Authorization Example
   Endpoint	Role
   POST /orders	USER
   POST /refund	ADMIN
   Important Notes

Gateway is the main security layer.

Internal services are private inside Kubernetes network.

JWT Flow
Client
↓ JWT
Gateway validates JWT
↓
Gateway forwards JWT
↓
Downstream services
Correlation ID

Gateway generates:

X-Correlation-Id

and propagates it to:

downstream HTTP calls
Kafka events
logs
3. order-service
   Responsibilities
   Create order
   Store order
   Publish order event
   Start saga flow
   Database Status Example
   Status
   PENDING
   WAITING_PAYMENT
   PAID
   FAILED
   COMPLETED
   REFUNDED
   Flow
   Create Order
   ↓
   Publish ORDER_CREATED
   Important Notes
   User information extracted from JWT
   Correlation ID stored in logs/events
4. inventory-service
   Responsibilities
   Reserve stock
   Release stock
   Deduct stock
   Kafka Consumer

Consumes:

ORDER_CREATED
Kafka Producer

Publishes:

STOCK_RESERVED
STOCK_RELEASED
OUT_OF_STOCK
Important Notes

Inventory reservation is part of saga pattern.

5. payment-service
   Responsibilities
   Create payment link
   Handle payment callback/webhook
   Refund payment
   Publish payment event
   Dummy Payment Design

Instead of frontend UI:

Return dummy callback URLs.

Example:

{
"successUrl": "/payment/callback/success?orderId=123",
"failedUrl": "/payment/callback/failed?orderId=123"
}
Callback Flow
Success
POST /payment/callback/success
↓
Publish PAYMENT_SUCCESS
Failed
POST /payment/callback/failed
↓
Publish PAYMENT_FAILED
Refund Flow

Consumes:

REFUND_REQUESTED

Publishes:

PAYMENT_REFUNDED
6. fulfillment-service
   Responsibilities
   Fulfill order
   Handle compensation logic
   Coordinate final saga result
   Kafka Consumers

Consumes:

PAYMENT_SUCCESS
PAYMENT_FAILED
Business Logic
If payment failed
Release inventory
Mark order FAILED
If fulfillment failed
Request refund
Release inventory
If fulfillment success
Mark order COMPLETED
Important Notes

This service handles distributed transaction compensation.

Infrastructure Components
Redis
Responsibilities
Idempotency
Deduplication
Event checkpoint
Distributed lock (optional)
Example
eventId -> processed
Deduplication Flow
Receive Kafka event
↓
Check Redis
↓
Already exists?
↓ yes
Ignore
↓ no
Process event
Save eventId
Recommended TTL
24 hours
Kafka Topics
Suggested Topics
order-created
stock-reserved
stock-released
payment-success
payment-failed
refund-requested
payment-refunded
fulfillment-success
fulfillment-failed
Correlation ID
Purpose

Distributed tracing across services.

Example Header
X-Correlation-Id
Must Be Propagated To
HTTP requests
Kafka events
Logs
Example Event
{
"eventId": "abc-123",
"correlationId": "corr-999",
"orderId": "order-1"
}
JWT Propagation
Strategy

Gateway validates JWT.

Gateway forwards JWT downstream.

Benefits

Services can access:

userId
role
email

without calling auth-service again.

Kubernetes Architecture
No Eureka Needed

Kubernetes already provides:

service discovery
DNS resolution
load balancing
Example
http://payment-service

works automatically inside cluster.

Configuration Management
No Spring Config Server

Use:

ConfigMap
Secret
Environment Variables

instead.

ConfigMap Example
env:
- name: APP_PAYMENT_TIMEOUT
  value: "30"

Spring Boot automatically maps:

APP_PAYMENT_TIMEOUT

to:

app.payment-timeout
Secrets Example

Use Kubernetes Secret for:

DB password
JWT secret
Kafka credentials
Recommended Development Phases
Phase 1 — MVP
Features
JWT auth
Gateway authorization
Create order
Kafka event flow
Dummy payment callback
Fulfillment flow
No Kubernetes yet

Use Docker Compose first.

Phase 2 — Reliability
Add
Redis idempotency
Retry
Dead Letter Queue (DLQ)
Compensation flow
Phase 3 — Observability
Add
Correlation ID logging
Distributed tracing
Metrics
Centralized logging
Phase 4 — Kubernetes
Add
Kubernetes deployment
ConfigMap
Secret
Horizontal scaling
Recommended Project Structure
auth-service
gateway-service
order-service
inventory-service
payment-service
fulfillment-service

Infrastructure:

Kafka
Redis
PostgreSQL
Docker
Kubernetes
Important Enterprise Concepts Used
Concept	Used
JWT Authentication	YES
Authorization	YES
API Gateway	YES
Reactive Programming	YES
Saga Pattern	YES
Compensation Transaction	YES
Event-Driven Architecture	YES
Kafka Messaging	YES
Idempotency	YES
Distributed Tracing	YES
Correlation ID	YES
Kubernetes	YES
Docker	YES
Future Improvements (Optional)

After MVP is stable:

Outbox Pattern
mTLS
Service Mesh
OpenTelemetry
Circuit Breaker
Rate Limiter
Multi-tenancy
CQRS/Event Sourcing
gRPC internal communication
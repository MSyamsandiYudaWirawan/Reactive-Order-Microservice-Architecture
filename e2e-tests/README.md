# E2E Tests (Newman / Postman CLI)

Automated end-to-end tests for the Reactive Order Microservice Architecture using [Newman](https://github.com/postmanlabs/newman).

## Prerequisites

```bash
npm install -g newman
npm install -g newman-reporter-htmlextra  # optional: HTML reports
```

## Test Suites

### Fast Tests (instant flow, ~2 min total)

| Suite | File | Flow Tested |
|-------|------|-------------|
| Auth Flow | `auth-flow` | Register → Login → Refresh → Invalid token (401) → Duplicate email (409) |
| Happy Path | `happy-path` | Register → Login → Order → WAITING_PAYMENT → Pay → Webhook SUCCESS → COMPLETED |
| Idempotency | `idempotency-test` | Same X-Idempotency-Key → same result, missing key → 400 |

### Compensation Tests — Instant (no scheduler wait)

| Suite | File | Compensation Flow |
|-------|------|-------------------|
| Out of Stock | `out-of-stock` | Order qty > available → saga FAILED → order OUT_OF_STOCK (no refund needed) |
| Payment Failed + Retry | `payment-failed-retry` | Webhook FAILED → order stays WAITING_PAYMENT → user retries → SUCCESS → COMPLETED |
| Refund (stock exhaustion) | `refund-flow` | Order 1 reserves 55/60 → Order 2 (10) gets OUT_OF_STOCK → Order 1 completes |

### Compensation Tests — Scheduler-Dependent (~2 min each, opt-in)

| Suite | File | Compensation Flow | Timing |
|-------|------|-------------------|--------|
| Order Expiry | `order-expiry` | Stock reserved → never pay → scheduler expires order + releases stock | 90s expiry + 30s scheduler |
| Payment Expiry | `payment-expiry` | Payment created → no webhook → payment expires (30s) → order expires (90s) | ~2 min total |

## Run All Tests

### Windows
```bash
cd e2e-tests

# Fast + instant compensation tests only
run-all.bat

# Include scheduler-dependent compensation tests
run-all.bat --with-scheduler

# AWS ALB
run-all.bat http://your-alb-dns.amazonaws.com 80

# AWS ALB + scheduler tests
run-all.bat http://your-alb-dns.amazonaws.com 80 3000 --with-scheduler
```

### Linux/Mac
```bash
cd e2e-tests
chmod +x run-all.sh

./run-all.sh
./run-all.sh --with-scheduler
./run-all.sh http://your-alb-dns.amazonaws.com 80 3000 --with-scheduler
```

## Run Individual Test

```bash
# Fast test
newman run happy-path.postman_collection.json -e environment.json --delay-request 3000

# Scheduler test (needs longer polling)
newman run order-expiry.postman_collection.json -e environment.json --delay-request 15000

# With HTML report
newman run happy-path.postman_collection.json -e environment.json --delay-request 3000 -r htmlextra
```

## Scheduler Timing Configuration

Current testing values (in source code):

| Config | Value | Location |
|--------|-------|----------|
| `paymentExpirySeconds` | 30s | payment-service `AppProperties.java` |
| `orderExpirySeconds` | 90s | orchestrator-service `AppProperties.java` |
| Scheduler cron | every 30s | Both `SchedulerController.java` |

The logic: payment expires first (30s) → payment-service marks FAILED → orchestrator picks up on next cycle (30s) → if order also expired (90s) → marks EXPIRED + releases stock/refunds.

## Configuration

Edit `environment.json` or pass parameters to the runner script:

| Variable | Default | Description |
|----------|---------|-------------|
| `base_url` | `http://localhost` | Base URL of your deployment |
| `gateway_port` | `8080` | Gateway port (use `80` for ALB) |
| `product_id` | `d4e5f6a7-...` | Product ID from init.sql seed data |
| `test_password` | `TestUser_1!` | Password for test users |

## Notes

- Each test suite creates its own user (unique email with timestamp) — no shared state between runs
- Fast tests poll up to 5 retries with `--delay-request` between each
- Scheduler tests poll up to 8-10 retries with 15s delay (covers 90s expiry + 30s scheduler)
- Increase `--delay-request` if tests fail on slower environments

## CI/CD Integration (GitHub Actions)

```yaml
- name: Run E2E Tests
  run: |
    npm install -g newman
    cd e2e-tests
    ./run-all.sh http://${{ env.ALB_DNS }} 80 3000 --with-scheduler
```

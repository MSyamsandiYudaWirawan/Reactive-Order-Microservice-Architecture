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
| Happy Path | `happy-path` | Register → Login → Order → WAITING_PAYMENT → Pay → Webhook SUCCESS → COMPLETED |
| Payment Failed + Retry | `payment-failed-retry` | Webhook FAILED → order stays WAITING_PAYMENT → user retries → SUCCESS → COMPLETED |
| Out of Stock | `out-of-stock` | Order qty=99999 → saga FAILED → order OUT_OF_STOCK (no refund needed) |
| Refund Flow | `refund-flow` | Order qty=99999 → Pay immediately → Webhook SUCCESS → OUT_OF_STOCK → REFUND → REFUNDED |
| Refund Failed + DLQ | `refund-failed-dlq` | Same as refund flow but Webhook REFUND_FAILED → REFUND_FAILED + payment-dlq |

### Compensation Tests — Scheduler-Dependent (~2 min each, opt-in)

| Suite | File | Compensation Flow | Timing |
|-------|------|-------------------|--------|
| Order Expiry | `order-expiry` | Stock reserved → never pay → scheduler expires order + releases stock | 90s expiry + 30s scheduler |
| Payment Expiry | `payment-expiry` | Payment created → no webhook → payment expires (30s) → order expires (90s) | ~2 min total |
| Late Webhook | `late-webhook` | Payment expired (FAILED) → late PAYMENT_SUCCESS webhook → silent refund, no event | ~2 min total |

---

## Run All Tests (Local Docker)

### Windows
```bash
cd e2e-tests

# Fast + instant compensation tests only
run-all.bat

# Include scheduler-dependent compensation tests
run-all.bat --with-scheduler

# With database cleanup before tests
run-with-cleanup.bat
run-with-cleanup.bat --with-scheduler
```

### Linux/Mac
```bash
cd e2e-tests
chmod +x run-all.sh run-with-cleanup.sh

./run-all.sh
./run-all.sh --with-scheduler

./run-with-cleanup.sh
./run-with-cleanup.sh --with-scheduler
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

---

## Scheduler Timing Configuration

Current testing values (in source code):

| Config | Value | Location |
|--------|-------|----------|
| `paymentExpirySeconds` | 30s | payment-service `AppProperties.java` |
| `orderExpirySeconds` | 90s | orchestrator-service `AppProperties.java` |
| Scheduler cron | every 30s | Both `SchedulerController.java` |

The logic: payment expires first (30s) → payment-service marks FAILED → orchestrator picks up on next cycle (30s) → if order also expired (90s) → marks EXPIRED + releases stock/refunds.

---

## Configuration

Edit `environment.json` or pass parameters to the runner script:

| Variable | Default | Description |
|----------|---------|-------------|
| `base_url` | `http://localhost` | Base URL of your deployment |
| `gateway_port` | `8080` | Gateway port (use `80` for ALB) |
| `product_id` | `d4e5f6a7-...` | Product ID from init.sql seed data |
| `test_password` | `Syamsandi_1!` | Password for test users |

---

## Database Cleanup

To avoid conflicts from previous test runs, use the built-in cleanup scripts:

### Auto-Cleanup (Recommended)
The test runners automatically clean databases before running tests:

```bash
# Windows
run-with-cleanup.bat              # Fast tests + cleanup
run-with-cleanup.bat --with-scheduler  # All tests + cleanup

# Linux/Mac
./run-with-cleanup.sh
./run-with-cleanup.sh --with-scheduler
```

### Manual Cleanup
Clean databases without running tests:

```bash
# Windows (PowerShell)
.\cleanup-dbs.ps1
.\cleanup-dbs.ps1 -Force

# Windows (cmd)
cleanup-dbs.bat
cleanup-dbs.bat --force

# Linux/Mac
./cleanup-dbs.sh
./cleanup-dbs.sh --force
```

### What Gets Cleaned

| Database | Cleaned | Preserved (Reference Data) |
|----------|----------|---------------------------|
| `auth_service_db` | ✅ `users` | None |
| `order_service_db` | ✅ `orders`, `order_items`, `order_ledger` | ✅ `discounts` |
| `inventory_service_db` | ✅ `stock_reservation`, `stock_ledger` | ✅ `products` |
| `payment_service_db` | ✅ `payments`, `payment_ledger` | None |
| `orchestrator_service_db` | ✅ `saga_state` | None |

**Standard cleanup** (default) preserves reference data (`products`, `discounts`) and resets stock quantities.

**Force cleanup** (`--force` flag) cleans ALL tables including reference data.

### Verify Cleanup

```bash
# Windows
verify-cleanup.bat

# Linux/Mac
./verify-cleanup.sh
```

---

## AWS Cloud Testing

Dedicated scripts for testing against the AWS ECS Fargate deployment (ALB → gateway-service).

### Get ALB DNS

```bash
cd terraform && terraform output alb_dns_name
```

### Run Tests Against AWS

```bash
# Windows
run-all-aws.bat <ALB_DNS>
run-all-aws.bat <ALB_DNS> --with-scheduler
run-all-aws.bat <ALB_DNS> --with-scheduler --with-cleanup

# Linux/Mac
chmod +x run-all-aws.sh
./run-all-aws.sh <ALB_DNS>
./run-all-aws.sh <ALB_DNS> --with-scheduler
./run-all-aws.sh <ALB_DNS> --with-scheduler --with-cleanup
```

### AWS Database Cleanup

Requires network access to RDS (via bastion host, VPN, or SSM port forwarding).

Set environment variables for RDS credentials:

```bash
# Individual hosts (from terraform output or AWS console)
export AWS_RDS_HOST_AUTH=reactive-order-auth.xxxxx.ap-southeast-3.rds.amazonaws.com
export AWS_RDS_HOST_ORDER=reactive-order-order.xxxxx.ap-southeast-3.rds.amazonaws.com
export AWS_RDS_HOST_INVENTORY=reactive-order-inventory.xxxxx.ap-southeast-3.rds.amazonaws.com
export AWS_RDS_HOST_PAYMENT=reactive-order-payment.xxxxx.ap-southeast-3.rds.amazonaws.com
export AWS_RDS_HOST_ORCHESTRATOR=reactive-order-orchestrator.xxxxx.ap-southeast-3.rds.amazonaws.com

# Passwords (from AWS Secrets Manager)
export AWS_RDS_PASSWORD_AUTH=<from-secrets-manager>
export AWS_RDS_PASSWORD_ORDER=<from-secrets-manager>
export AWS_RDS_PASSWORD_INVENTORY=<from-secrets-manager>
export AWS_RDS_PASSWORD_PAYMENT=<from-secrets-manager>
export AWS_RDS_PASSWORD_ORCHESTRATOR=<from-secrets-manager>
```

Then run cleanup:

```bash
# Linux/Mac
./cleanup-dbs-aws.sh
./cleanup-dbs-aws.sh --force

# Windows (PowerShell)
.\cleanup-dbs-aws.ps1
.\cleanup-dbs-aws.ps1 -Force
```

### AWS vs Local Differences

| Aspect | Local (Docker) | AWS (ECS Fargate) |
|--------|---------------|-------------------|
| URL | `http://localhost` | `http://<ALB-DNS>` |
| Port | `8080` | `80` |
| Delay | 5s (poll) | 8s (poll), 15s (scheduler) |
| Refund/DLQ delay | 3s (race condition window) | 3s (same — must fire before 15s stock check) |
| DB Cleanup | `docker exec` | `psql` to RDS (needs network access) |
| DLQ Verify | `docker exec kafka-console-consumer` | Skip (no direct Kafka access from outside VPC) |

### SSM Port Forwarding (for DB Cleanup)

If you don't have a bastion host, use AWS SSM to tunnel to RDS:

```bash
# Forward local port 5432 to RDS through an ECS task
aws ssm start-session \
  --target <ecs-task-id> \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["reactive-order-auth.xxxxx.rds.amazonaws.com"],"portNumber":["5432"],"localPortNumber":["5432"]}'
```

Then set `AWS_RDS_HOST_*=localhost` and run cleanup scripts.

---

## File Reference

| File | Purpose |
|------|---------|
| **Test Collections** | |
| `happy-path.postman_collection.json` | Happy path: order → pay → COMPLETED |
| `payment-failed-retry.postman_collection.json` | Payment fails → retry → COMPLETED |
| `out-of-stock.postman_collection.json` | Qty exceeds stock → OUT_OF_STOCK |
| `refund-flow.postman_collection.json` | Paid + OUT_OF_STOCK → REFUNDED |
| `refund-failed-dlq.postman_collection.json` | Refund fails → REFUND_FAILED + DLQ |
| `order-expiry.postman_collection.json` | Never paid → scheduler EXPIRED |
| `payment-expiry.postman_collection.json` | No webhook → payment expires → order EXPIRED |
| `late-webhook.postman_collection.json` | Late webhook after payment expired → silent refund |
| **Runners (Local)** | |
| `run-all.sh` / `run-all.bat` | Run all tests (local Docker) |
| `run-with-cleanup.sh` / `run-with-cleanup.bat` | Cleanup DBs + run all tests |
| **Runners (AWS)** | |
| `run-all-aws.sh` / `run-all-aws.bat` | Run all tests against ALB |
| **Cleanup (Local)** | |
| `cleanup-dbs.sh` / `cleanup-dbs.ps1` / `cleanup-dbs.bat` | Clean Docker databases |
| `verify-cleanup.sh` / `verify-cleanup.bat` | Verify cleanup preserved reference data |
| **Cleanup (AWS)** | |
| `cleanup-dbs-aws.sh` / `cleanup-dbs-aws.ps1` | Clean RDS databases |
| **Environments** | |
| `environment.json` | Local Docker environment |
| `environment-aws.json` | AWS ALB environment |
| **SQL** | |
| `truncate.sql` | Truncate all tables |
| `truncate-preserve-discounts.sql` | Truncate except discounts |
| `truncate-preserve-products.sql` | Truncate except products |

---

## Troubleshooting

### 409 Conflict on Registration
Each test suite generates unique emails (`e2e_<timestamp>@testmail.com`). If you still get 409:
1. Use `run-with-cleanup` scripts instead of `run-all`
2. Or run cleanup manually before tests

### Timeout on Polling
- Increase `--delay-request` for slower environments
- Check service health: `curl http://<host>:<port>/actuator/health`

### Refund/DLQ Tests Fail (Order becomes OUT_OF_STOCK instead of REFUNDED)
- The payment + webhook must fire **before** the 15s stock delay completes
- Ensure delay is 3s (not 8s) for `refund-flow` and `refund-failed-dlq` collections
- If network latency is high, reduce stock delay in `inventory-service` for testing

### Scheduler Tests Timeout
- Ensure payment expiry (30s) + order expiry (90s) + scheduler cycle (30s) fits within polling window
- Default: 8-12 retries × 15s = 120-180s coverage

---

## CI/CD Integration (GitHub Actions)

```yaml
- name: Run E2E Tests (AWS)
  env:
    ALB_DNS: ${{ steps.terraform.outputs.alb_dns_name }}
  run: |
    npm install -g newman
    cd e2e-tests
    ./run-all-aws.sh $ALB_DNS --with-scheduler
```

---

## Notes

- Each test suite creates its own user (unique email with timestamp) — no shared state between runs
- Environment is reset between each test suite in the AWS runner
- Fast tests poll up to 10 retries with `--delay-request` between each
- Scheduler tests poll up to 8-12 retries with 15s delay (covers 90s expiry + 30s scheduler)
- Refund/DLQ tests use 3s delay to preserve the race condition timing window

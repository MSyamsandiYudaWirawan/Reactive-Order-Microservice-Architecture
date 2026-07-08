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
# Windows - Standard cleanup (preserves reference data)
cleanup-dbs.bat                  # Local Docker databases
cleanup-dbs.bat aws              # AWS RDS (requires AWS_RDS_HOST env var)

# Windows - Force clean (ALL tables including reference data)
cleanup-dbs.bat --force
cleanup-dbs.bat aws --force

# Linux/Mac - Standard cleanup (preserves reference data)
./cleanup-dbs.sh
./cleanup-dbs.sh aws

# Linux/Mac - Force clean (ALL tables including reference data)
./cleanup-dbs.sh --force
./cleanup-dbs.sh aws --force
```

### What Gets Cleaned
The cleanup script selectively truncates tables in 5 databases:

| Database | Port | Cleaned | Preserved (Reference Data) |
|----------|------|----------|---------------------------|
| `auth_service_db` | 5432 | ✅ `users` | None |
| `order_service_db` | 5433 | ✅ `orders`, `order_items`, `order_ledger` | ✅ `discounts` |
| `inventory_service_db` | 5434 | ✅ `stock_reservation`, `stock_ledger` | ✅ `products` |
| `payment_service_db` | 5435 | ✅ `payments`, `payment_ledger` | None |
| `orchestrator_service_db` | 5436 | ✅ `saga_state` | None |

**Standard cleanup** (default) preserves reference data:
- `products` table in inventory service (sample products for testing)
- `discounts` table in order service (discount codes)

**Force cleanup** (`--force` flag) cleans ALL tables including reference data.

This ensures each test run starts with a clean slate while preserving your test data.

## Troubleshooting

### 409 Conflict on Registration
If you see `409 Conflict` on `/api/v1/auth/register`:

1. **Ensure cleanup runs** - Use `run-with-cleanup.sh` instead of `run-all.sh`
2. **Clear Postman environment** - Delete the `test_email` variable in Postman
3. **Check database** - Verify cleanup script succeeded (look for "All databases cleaned successfully")

The test suites now generate unique emails automatically (`e2e_<timestamp>@testmail.com`), so this should be resolved with the auto-cleanup scripts.

## Notes

- Each test suite creates its own user (unique email with timestamp) — no shared state between runs
- Auto-cleanup scripts are now integrated into test runners to prevent conflicts
- Fast tests poll up to 5 retries with `--delay-request` between each
- Scheduler tests poll up to 8-10 retries with 15s delay (covers 90s expiry + 30s scheduler)
- Increase `--delay-request` if tests fail on slower environments

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

### AWS Test Files

| File | Purpose |
|------|--------|
| `run-all-aws.sh` | Main test runner (Linux/Mac) |
| `run-all-aws.bat` | Main test runner (Windows) |
| `cleanup-dbs-aws.sh` | RDS cleanup (Linux/Mac) |
| `cleanup-dbs-aws.ps1` | RDS cleanup (Windows/PowerShell) |
| `environment-aws.json` | Environment variables for AWS |

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

#!/bin/bash
# ============================================================
# E2E Test Runner for AWS (Newman / Postman CLI)
# ============================================================
# Runs all E2E tests against the AWS ECS Fargate deployment.
#
# Usage:
#   ./run-all-aws.sh <ALB_DNS>                          (fast tests only)
#   ./run-all-aws.sh <ALB_DNS> --with-scheduler         (include scheduler tests)
#   ./run-all-aws.sh <ALB_DNS> --with-cleanup           (cleanup DBs before tests)
#   ./run-all-aws.sh <ALB_DNS> --with-scheduler --with-cleanup
#
# Examples:
#   ./run-all-aws.sh reactive-order-alb-123456.ap-southeast-3.elb.amazonaws.com
#   ./run-all-aws.sh $ALB_DNS --with-scheduler --with-cleanup
#
# Environment:
#   ALB_DNS (optional) - Can also be passed as env var instead of argument
# ============================================================

WITH_SCHEDULER=0
WITH_CLEANUP=0
ALB_DNS="${ALB_DNS:-}"

# Parse arguments
for arg in "$@"; do
    case "$arg" in
        --with-scheduler) WITH_SCHEDULER=1 ;;
        --with-cleanup) WITH_CLEANUP=1 ;;
        *) ALB_DNS="$arg" ;;
    esac
done

if [ -z "$ALB_DNS" ]; then
    echo "[ERROR] ALB DNS name is required."
    echo ""
    echo "Usage: ./run-all-aws.sh <ALB_DNS> [--with-scheduler] [--with-cleanup]"
    echo ""
    echo "Get ALB DNS from Terraform:"
    echo "  cd terraform && terraform output alb_dns_name"
    exit 1
fi

# Strip protocol if provided
ALB_DNS="${ALB_DNS#http://}"
ALB_DNS="${ALB_DNS#https://}"

BASE_URL="http://${ALB_DNS}"
GATEWAY_PORT="80"
DELAY_MS="5000"
POLL_DELAY_MS="8000"
SCHEDULER_DELAY_MS="15000"

echo ""
echo "============================================================"
echo "  Reactive Order Microservice - E2E Tests (AWS)"
echo "  Target: ${BASE_URL}:${GATEWAY_PORT}"
echo "  Delay: ${DELAY_MS}ms (fast) / ${POLL_DELAY_MS}ms (poll) / ${SCHEDULER_DELAY_MS}ms (scheduler)"
[ $WITH_SCHEDULER -eq 1 ] && echo "  Scheduler tests: ENABLED"
[ $WITH_CLEANUP -eq 1 ] && echo "  DB Cleanup: ENABLED"
echo "============================================================"
echo ""

# Check newman
if ! command -v newman &> /dev/null; then
    echo "[ERROR] Newman is not installed. Install with:"
    echo "  npm install -g newman"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/environment-aws.json"

# Optional: cleanup databases before tests
if [ $WITH_CLEANUP -eq 1 ]; then
    echo "[1/2] Cleaning AWS RDS databases..."
    if "${SCRIPT_DIR}/cleanup-dbs-aws.sh"; then
        echo ""
    else
        echo "[WARN] Database cleanup failed — continuing with tests anyway"
        echo ""
    fi
fi

PASSED=0
FAILED=0
TOTAL=0

# Reset environment for each test run
reset_env() {
    cat > "$ENV_FILE" << EOF
{
  "id": "e2e-env-aws",
  "name": "E2E Test Environment (AWS)",
  "values": [
    {"key": "base_url", "value": "${BASE_URL}", "enabled": true},
    {"key": "gateway_port", "value": "${GATEWAY_PORT}", "enabled": true},
    {"key": "test_email", "value": "", "enabled": true},
    {"key": "test_phone", "value": "", "enabled": true},
    {"key": "test_password", "value": "Syamsandi_1!", "enabled": true},
    {"key": "test_name", "value": "Syamsandi", "enabled": true},
    {"key": "access_token", "value": "", "enabled": true},
    {"key": "refresh_token", "value": "", "enabled": true},
    {"key": "transaction_id", "value": "", "enabled": true},
    {"key": "payment_id", "value": "", "enabled": true},
    {"key": "product_id", "value": "d4e5f6a7-b8c9-0123-defa-234567890123", "enabled": true}
  ]
}
EOF
}

run_test() {
    local name="$1"
    local file="$2"
    local delay="${3:-$POLL_DELAY_MS}"
    local timeout="${4:-30000}"
    TOTAL=$((TOTAL + 1))

    echo ""
    echo "[${TOTAL}] ${name}"
    echo "------------------------------------------------------------"
    reset_env

    if newman run "${SCRIPT_DIR}/${file}" \
        -e "$ENV_FILE" \
        --delay-request "$delay" \
        --timeout-request "$timeout"; then
        PASSED=$((PASSED + 1))
    else
        FAILED=$((FAILED + 1))
    fi
}

echo ""
echo "============================"
echo "  FAST TESTS (instant flow)"
echo "============================"

run_test "Happy Path (Order COMPLETED)" "happy-path.postman_collection.json" "$POLL_DELAY_MS"
run_test "Payment Failed + Retry (COMPLETED)" "payment-failed-retry.postman_collection.json" "$POLL_DELAY_MS"
run_test "Out of Stock - No Payment (OUT_OF_STOCK)" "out-of-stock.postman_collection.json" "$POLL_DELAY_MS"
run_test "Refund Flow: Out of Stock + Paid (REFUNDED)" "refund-flow.postman_collection.json" "3000"
run_test "Refund Failed + DLQ (manual intervention)" "refund-failed-dlq.postman_collection.json" "3000"

if [ $WITH_SCHEDULER -eq 1 ]; then
    echo ""
    echo "============================================"
    echo "  SCHEDULER TESTS (requires waiting)"
    echo "  Payment expiry=30s, Order expiry=90s"
    echo "  Polling every ${SCHEDULER_DELAY_MS}ms"
    echo "============================================"

    run_test "Order Expiry - Never Paid (~2min)" "order-expiry.postman_collection.json" "$SCHEDULER_DELAY_MS" 60000
    run_test "Payment Expiry - No Webhook (~2min)" "payment-expiry.postman_collection.json" "$SCHEDULER_DELAY_MS" 60000
    run_test "Late Webhook After Payment Expired (~2min)" "late-webhook.postman_collection.json" "$SCHEDULER_DELAY_MS" 60000
fi

# Summary
echo ""
echo "============================================================"
echo "  TEST SUMMARY (AWS)"
echo "============================================================"
echo "  Target: ${BASE_URL}:${GATEWAY_PORT}"
echo "  Total:  ${TOTAL}"
echo "  Passed: ${PASSED}"
echo "  Failed: ${FAILED}"
echo "============================================================"

if [ "$FAILED" -gt 0 ]; then
    echo "  ❌ SOME TESTS FAILED"
    exit 1
else
    echo "  ✅ ALL TESTS PASSED"
    exit 0
fi

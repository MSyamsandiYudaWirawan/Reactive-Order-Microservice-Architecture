#!/bin/bash
# ============================================================
# E2E Test Runner - Newman (Postman CLI)
# ============================================================
# Usage:
#   ./run-all.sh                                    (fast tests only)
#   ./run-all.sh --with-scheduler                   (include scheduler tests)
#   ./run-all.sh http://your-alb-url 80             (AWS ALB)
#   ./run-all.sh http://localhost 8080 3000 --with-scheduler
# ============================================================

BASE_URL="http://localhost"
GATEWAY_PORT="8080"
DELAY_MS="3000"
WITH_SCHEDULER=0

# Parse arguments
args=()
for arg in "$@"; do
    if [ "$arg" = "--with-scheduler" ]; then
        WITH_SCHEDULER=1
    else
        args+=("$arg")
    fi
done
[ ${#args[@]} -ge 1 ] && BASE_URL="${args[0]}"
[ ${#args[@]} -ge 2 ] && GATEWAY_PORT="${args[1]}"
[ ${#args[@]} -ge 3 ] && DELAY_MS="${args[2]}"

echo ""
echo "============================================================"
echo "  Reactive Order Microservice - E2E Tests"
echo "  Target: ${BASE_URL}:${GATEWAY_PORT}"
echo "  Delay: ${DELAY_MS}ms (fast) / 15000ms (scheduler)"
[ $WITH_SCHEDULER -eq 1 ] && echo "  Scheduler tests: ENABLED"
echo "============================================================"
echo ""

if ! command -v newman &> /dev/null; then
    echo "[ERROR] Newman is not installed. Install with:"
    echo "  npm install -g newman"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/environment.json"

cat > "$ENV_FILE" << EOF
{
  "id": "e2e-env",
  "name": "E2E Test Environment",
  "values": [
    {"key": "base_url", "value": "${BASE_URL}", "enabled": true},
    {"key": "gateway_port", "value": "${GATEWAY_PORT}", "enabled": true},
    {"key": "test_email", "value": "", "enabled": true},
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

PASSED=0
FAILED=0
TOTAL=0

run_test() {
    local name="$1"
    local file="$2"
    local delay="${3:-$DELAY_MS}"
    local timeout="${4:-30000}"
    TOTAL=$((TOTAL + 1))

    echo ""
    echo "[${TOTAL}] ${name}"
    echo "------------------------------------------------------------"

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

run_test "Auth Flow" "auth-flow.postman_collection.json"
run_test "Happy Path (Order COMPLETED)" "happy-path.postman_collection.json"
run_test "Idempotency" "idempotency-test.postman_collection.json"

echo ""
echo "===================================="
echo "  COMPENSATION TESTS (instant flow)"
echo "===================================="

run_test "Compensation: Out of Stock (no payment needed)" "out-of-stock.postman_collection.json"
run_test "Compensation: Payment Failed + Retry" "payment-failed-retry.postman_collection.json"
run_test "Compensation: Refund (stock exhaustion)" "refund-flow.postman_collection.json"

if [ $WITH_SCHEDULER -eq 1 ]; then
    echo ""
    echo "============================================"
    echo "  COMPENSATION TESTS (scheduler-dependent)"
    echo "  Polling every 15s, payment expiry=30s,"
    echo "  order expiry=90s, scheduler=every 30s"
    echo "============================================"

    run_test "Compensation: Order Expiry (never paid, ~2min)" "order-expiry.postman_collection.json" 15000 60000
    run_test "Compensation: Payment Expiry (no webhook, ~2min)" "payment-expiry.postman_collection.json" 15000 60000
fi

echo ""
echo "============================================================"
echo "  TEST SUMMARY"
echo "============================================================"
echo "  Total:  ${TOTAL}"
echo "  Passed: ${PASSED}"
echo "  Failed: ${FAILED}"
echo "============================================================"

if [ "$FAILED" -gt 0 ]; then
    echo "  SOME TESTS FAILED"
    exit 1
else
    echo "  ALL TESTS PASSED"
    exit 0
fi

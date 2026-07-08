#!/bin/bash
# ============================================================
# E2E Test Runner with Automatic Database Cleanup
# ============================================================
# This script is a shortcut that:
#   1. Cleans all databases
#   2. Runs all E2E tests
# Usage:
#   ./run-with-cleanup.sh                        (fast tests only)
#   ./run-with-cleanup.sh --with-scheduler        (include scheduler tests)
#   ./run-with-cleanup.sh http://your-alb-url 80  (AWS ALB)
# ============================================================

echo "============================================================"
echo "  E2E Test Runner with Database Cleanup"
echo "============================================================"
echo ""

echo "[1/2] Cleaning databases..."
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
"${SCRIPT_DIR}/cleanup-dbs.sh"

if [ $? -ne 0 ]; then
    echo "[ERROR] Database cleanup failed. Aborting tests."
    exit 1
fi

echo ""
echo "[2/2] Running E2E tests..."
"${SCRIPT_DIR}/run-all.sh" "$@"

exit $?

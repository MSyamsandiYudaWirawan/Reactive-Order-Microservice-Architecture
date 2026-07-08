#!/bin/bash
# ============================================================
# E2E Test Database Cleanup Script (Docker)
# ============================================================
# This script clears test data from all 5 databases before E2E tests
# Usage: ./cleanup-dbs.sh [--force]
#   --force: Clean ALL tables including reference data (products, discounts)
#
# Preserved by default (reference data):
#   - inventory_service_db.products
#   - order_service_db.discounts
# ============================================================

FORCE_CLEAN=0

# Parse arguments
for arg in "$@"; do
    if [ "$arg" = "--force" ]; then
        FORCE_CLEAN=1
    fi
done

if [ $FORCE_CLEAN -eq 1 ]; then
    echo "[INFO] FORCE CLEAN enabled - ALL tables will be cleaned including reference data"
else
    echo "[INFO] Standard cleanup - Preserving reference data (products, discounts)"
fi

DB_USER="username"

echo "============================================================"
echo "  E2E Test Database Cleanup"
echo "  Target: Docker containers"
echo "============================================================"
echo ""

TOTAL=0
SUCCESS=0
FAILED=0

# ========================================
# Function to clean a database
# ========================================
clean_db() {
    local DB_CONTAINER=$1
    local DB_NAME=$2
    local DB_DISPLAY=$3
    local PRESERVE_TABLES=$4

    TOTAL=$((TOTAL + 1))
    echo "[$TOTAL] Cleaning $DB_DISPLAY..."

    # Build TRUNCATE query with preserved tables
    local truncate_query
    if [ -z "$PRESERVE_TABLES" ]; then
        # Clean all tables except migrations
        truncate_query="DO \$\$ DECLARE table_name TEXT; BEGIN FOR table_name IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename NOT IN ('schema_version', 'flyway_schema_history') ORDER BY tablename) LOOP EXECUTE 'TRUNCATE TABLE ' || table_name || ' CASCADE;'; RAISE NOTICE 'Truncated table: %', table_name; END LOOP; END \$\$;"
    else
        # Clean tables except preserved ones + migrations
        truncate_query="DO \$\$ DECLARE table_name TEXT; BEGIN FOR table_name IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename NOT IN ('schema_version', 'flyway_schema_history', $PRESERVE_TABLES) ORDER BY tablename) LOOP EXECUTE 'TRUNCATE TABLE ' || table_name || ' CASCADE;'; RAISE NOTICE 'Truncated table: %', table_name; END LOOP; END \$\$;"
    fi

    if docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "$truncate_query" > /dev/null 2>&1; then
        echo "     ✅ Success - $DB_DISPLAY cleared"
        SUCCESS=$((SUCCESS + 1))
    else
        echo "     ❌ Failed - $DB_DISPLAY"
        echo "     Trying alternative cleanup method..."

        # Alternative: Drop and recreate schema
        if docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO username;" > /dev/null 2>&1; then
            echo "     ✅ Success - $DB_DISPLAY schema recreated"
            SUCCESS=$((SUCCESS + 1))
        else
            echo "     ❌ Still failed - $DB_DISPLAY"
            FAILED=$((FAILED + 1))
        fi
    fi
    echo ""
}

# ========================================
# Clean all databases (preserving reference data)
# ========================================

if [ $FORCE_CLEAN -eq 1 ]; then
    # Force clean - all tables
    clean_db "reactiveordermicroservicearchitecture-auth-db-1" "auth_service_db" "Auth DB" ""
    clean_db "reactiveordermicroservicearchitecture-order-db-1" "order_service_db" "Order DB" ""
    clean_db "reactiveordermicroservicearchitecture-inventory-db-1" "inventory_service_db" "Inventory DB" ""
    clean_db "reactiveordermicroservicearchitecture-payment-db-1" "payment_service_db" "Payment DB" ""
    clean_db "reactiveordermicroservicearchitecture-orchestrator-db-1" "orchestrator_service_db" "Orchestrator DB" ""
else
    # Standard cleanup - preserve reference data
    clean_db "reactiveordermicroservicearchitecture-auth-db-1" "auth_service_db" "Auth DB" ""
    clean_db "reactiveordermicroservicearchitecture-order-db-1" "order_service_db" "Order DB" "'discounts'"
    clean_db "reactiveordermicroservicearchitecture-inventory-db-1" "inventory_service_db" "Inventory DB" "'products'"
    clean_db "reactiveordermicroservicearchitecture-payment-db-1" "payment_service_db" "Payment DB" ""
    clean_db "reactiveordermicroservicearchitecture-orchestrator-db-1" "orchestrator_service_db" "Orchestrator DB" ""
fi

# ========================================
# Summary
# ========================================
echo "============================================================"
echo "  CLEANUP SUMMARY"
echo "============================================================"
echo "  Total:   $TOTAL"
echo "  Success: $SUCCESS"
echo "  Failed:  $FAILED"
echo "============================================================"

if [ "$FAILED" -gt 0 ]; then
    echo "[ERROR] Some databases failed to clean"
    exit 1
else
    echo "✅ All databases cleaned successfully"
    exit 0
fi

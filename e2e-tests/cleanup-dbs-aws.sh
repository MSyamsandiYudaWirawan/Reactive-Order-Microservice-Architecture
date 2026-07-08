#!/bin/bash
# ============================================================
# E2E Test Database Cleanup Script (AWS RDS)
# ============================================================
# This script clears test data from all 5 RDS databases before E2E tests.
# Requires network access to RDS (via bastion host, VPN, or SSM tunnel).
#
# Usage: ./cleanup-dbs-aws.sh [--force]
#   --force: Clean ALL tables including reference data (products, discounts)
#
# Required environment variables:
#   AWS_RDS_HOST_AUTH         - RDS endpoint for auth DB
#   AWS_RDS_HOST_ORDER        - RDS endpoint for order DB
#   AWS_RDS_HOST_INVENTORY    - RDS endpoint for inventory DB
#   AWS_RDS_HOST_PAYMENT      - RDS endpoint for payment DB
#   AWS_RDS_HOST_ORCHESTRATOR - RDS endpoint for orchestrator DB
#   AWS_RDS_PASSWORD_AUTH     - Password for auth DB
#   AWS_RDS_PASSWORD_ORDER    - Password for order DB
#   AWS_RDS_PASSWORD_INVENTORY - Password for inventory DB
#   AWS_RDS_PASSWORD_PAYMENT  - Password for payment DB
#   AWS_RDS_PASSWORD_ORCHESTRATOR - Password for orchestrator DB
#
# Or use a single host if all DBs are on the same RDS (different db names):
#   AWS_RDS_HOST              - Single RDS endpoint (overrides individual hosts)
# ============================================================

set -e

FORCE_CLEAN=0
for arg in "$@"; do
    [ "$arg" = "--force" ] && FORCE_CLEAN=1
done

if [ $FORCE_CLEAN -eq 1 ]; then
    echo "[INFO] FORCE CLEAN enabled - ALL tables"
else
    echo "[INFO] Standard cleanup - Preserving reference data (products, discounts)"
fi

echo "============================================================"
echo "  E2E Test Database Cleanup (AWS RDS)"
echo "============================================================"
echo ""

# DB config - usernames match Terraform: ${service}_admin
declare -A DB_CONFIG=(
    [auth]="auth_admin:auth_db:${AWS_RDS_HOST_AUTH:-$AWS_RDS_HOST}"
    [order]="order_admin:order_db:${AWS_RDS_HOST_ORDER:-$AWS_RDS_HOST}"
    [inventory]="inventory_admin:inventory_db:${AWS_RDS_HOST_INVENTORY:-$AWS_RDS_HOST}"
    [payment]="payment_admin:payment_db:${AWS_RDS_HOST_PAYMENT:-$AWS_RDS_HOST}"
    [orchestrator]="orchestrator_admin:orchestrator_db:${AWS_RDS_HOST_ORCHESTRATOR:-$AWS_RDS_HOST}"
)

declare -A DB_PASSWORDS=(
    [auth]="${AWS_RDS_PASSWORD_AUTH}"
    [order]="${AWS_RDS_PASSWORD_ORDER}"
    [inventory]="${AWS_RDS_PASSWORD_INVENTORY}"
    [payment]="${AWS_RDS_PASSWORD_PAYMENT}"
    [orchestrator]="${AWS_RDS_PASSWORD_ORCHESTRATOR}"
)

TOTAL=0
SUCCESS=0
FAILED=0

# SQL templates
TRUNCATE_ALL="DO \$\$ DECLARE t TEXT; BEGIN FOR t IN (SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('schema_version','flyway_schema_history') ORDER BY tablename) LOOP EXECUTE 'TRUNCATE TABLE ' || t || ' CASCADE;'; END LOOP; END \$\$;"
TRUNCATE_PRESERVE_DISCOUNTS="DO \$\$ DECLARE t TEXT; BEGIN FOR t IN (SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('schema_version','flyway_schema_history','discounts') ORDER BY tablename) LOOP EXECUTE 'TRUNCATE TABLE ' || t || ' CASCADE;'; END LOOP; END \$\$;"
TRUNCATE_PRESERVE_PRODUCTS="DO \$\$ DECLARE t TEXT; BEGIN FOR t IN (SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('schema_version','flyway_schema_history','products') ORDER BY tablename) LOOP EXECUTE 'TRUNCATE TABLE ' || t || ' CASCADE;'; END LOOP; END \$\$;"

RESET_STOCK="UPDATE products SET available_qty = CASE id WHEN 'a1b2c3d4-e5f6-7890-abcd-ef1234567890' THEN 100 WHEN 'b2c3d4e5-f6a7-8901-bcde-f12345678901' THEN 50 WHEN 'c3d4e5f6-a7b8-9012-cdef-123456789012' THEN 75 WHEN 'd4e5f6a7-b8c9-0123-defa-234567890123' THEN 30 WHEN 'e5f6a7b8-c9d0-1234-efab-345678901234' THEN 60 END, reserved_qty=0, sold_qty=0, last_modified_date=NOW() WHERE id IN ('a1b2c3d4-e5f6-7890-abcd-ef1234567890','b2c3d4e5-f6a7-8901-bcde-f12345678901','c3d4e5f6-a7b8-9012-cdef-123456789012','d4e5f6a7-b8c9-0123-defa-234567890123','e5f6a7b8-c9d0-1234-efab-345678901234');"

clean_db() {
    local service=$1
    local sql=$2
    local display=$3

    TOTAL=$((TOTAL + 1))
    echo "[$TOTAL] Cleaning $display..."

    IFS=':' read -r user dbname host <<< "${DB_CONFIG[$service]}"
    local password="${DB_PASSWORDS[$service]}"

    if [ -z "$host" ]; then
        echo "     ❌ Failed - No host configured for $service (set AWS_RDS_HOST_${service^^} or AWS_RDS_HOST)"
        FAILED=$((FAILED + 1))
        return
    fi

    if PGPASSWORD="$password" psql -h "$host" -p 5432 -U "$user" -d "$dbname" -c "$sql" > /dev/null 2>&1; then
        echo "     ✅ Success - $display"
        SUCCESS=$((SUCCESS + 1))
    else
        echo "     ❌ Failed - $display"
        FAILED=$((FAILED + 1))
    fi
}

# Clean databases
if [ $FORCE_CLEAN -eq 1 ]; then
    clean_db "auth" "$TRUNCATE_ALL" "Auth DB"
    clean_db "order" "$TRUNCATE_ALL" "Order DB (force)"
    clean_db "inventory" "$TRUNCATE_ALL" "Inventory DB (force)"
    clean_db "payment" "$TRUNCATE_ALL" "Payment DB"
    clean_db "orchestrator" "$TRUNCATE_ALL" "Orchestrator DB"
else
    clean_db "auth" "$TRUNCATE_ALL" "Auth DB"
    clean_db "order" "$TRUNCATE_PRESERVE_DISCOUNTS" "Order DB (discounts preserved)"
    clean_db "inventory" "$TRUNCATE_PRESERVE_PRODUCTS" "Inventory DB (products preserved)"
    clean_db "payment" "$TRUNCATE_ALL" "Payment DB"
    clean_db "orchestrator" "$TRUNCATE_ALL" "Orchestrator DB"
fi

# Reset product stock
TOTAL=$((TOTAL + 1))
echo "[$TOTAL] Restoring product stock..."
IFS=':' read -r user dbname host <<< "${DB_CONFIG[inventory]}"
if PGPASSWORD="${DB_PASSWORDS[inventory]}" psql -h "$host" -p 5432 -U "$user" -d "$dbname" -c "$RESET_STOCK" > /dev/null 2>&1; then
    echo "     ✅ Success - Product stock restored"
    SUCCESS=$((SUCCESS + 1))
else
    echo "     ❌ Failed - Product stock restore"
    FAILED=$((FAILED + 1))
fi

echo ""
echo "============================================================"
echo "  CLEANUP SUMMARY"
echo "============================================================"
echo "  Total:   $TOTAL"
echo "  Success: $SUCCESS"
echo "  Failed:  $FAILED"
echo "============================================================"

[ "$FAILED" -gt 0 ] && echo "[ERROR] Some databases failed to clean" && exit 1
echo "✅ All databases cleaned successfully"
exit 0

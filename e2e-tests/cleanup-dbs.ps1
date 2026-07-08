# ============================================================
# E2E Test Database Cleanup Script (Docker - PowerShell)
# ============================================================
# This script clears test data from all 5 databases before E2E tests
# Usage: .\cleanup-dbs.ps1 [-Force]
#   -Force: Clean ALL tables including reference data (products, discounts)
#
# Preserved by default (reference data):
#   - inventory_service_db.products
#   - order_service_db.discounts
# ============================================================

param(
    [switch]$Force
)

$ErrorActionPreference = "Continue"

if ($Force) {
    Write-Host "[INFO] FORCE CLEAN enabled - ALL tables will be cleaned including reference data"
} else {
    Write-Host "[INFO] Standard cleanup - Preserving reference data (products, discounts)"
}

Write-Host "============================================================"
Write-Host "  E2E Test Database Cleanup"
Write-Host "  Target: Docker containers"
Write-Host "============================================================"
Write-Host ""

$Total = 0
$Success = 0
$Failed = 0

# SQL: Reset product stock to initial values
$resetProductStockSQL = @"
UPDATE products SET available_qty = CASE id
    WHEN 'a1b2c3d4-e5f6-7890-abcd-ef1234567890' THEN 100
    WHEN 'b2c3d4e5-f6a7-8901-bcde-f12345678901' THEN 50
    WHEN 'c3d4e5f6-a7b8-9012-cdef-123456789012' THEN 75
    WHEN 'd4e5f6a7-b8c9-0123-defa-234567890123' THEN 30
    WHEN 'e5f6a7b8-c9d0-1234-efab-345678901234' THEN 60
    END,
    reserved_qty = 0,
    sold_qty = 0,
    last_modified_date = NOW()
WHERE id IN (
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    'b2c3d4e5-f6a7-8901-bcde-f12345678901',
    'c3d4e5f6-a7b8-9012-cdef-123456789012',
    'd4e5f6a7-b8c9-0123-defa-234567890123',
    'e5f6a7b8-c9d0-1234-efab-345678901234'
);
"@

# SQL templates
$truncateAllSQL = @"
DO \$$
DECLARE
    table_name TEXT;
BEGIN
    FOR table_name IN (
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
        AND tablename NOT IN ('schema_version', 'flyway_schema_history')
        ORDER BY tablename
    ) LOOP
        EXECUTE 'TRUNCATE TABLE ' || table_name || ' CASCADE;';
        RAISE NOTICE 'Truncated table: %', table_name;
    END LOOP;
END \$\$;
"@

$truncatePreserveDiscountsSQL = @"
DO \$$
DECLARE
    table_name TEXT;
BEGIN
    FOR table_name IN (
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
        AND tablename NOT IN ('schema_version', 'flyway_schema_history', 'discounts')
        ORDER BY tablename
    ) LOOP
        EXECUTE 'TRUNCATE TABLE ' || table_name || ' CASCADE;';
        RAISE NOTICE 'Truncated table: %', table_name;
    END LOOP;
END \$\$;
"@

$truncatePreserveProductsSQL = @"
DO \$$
DECLARE
    table_name TEXT;
BEGIN
    FOR table_name IN (
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
        AND tablename NOT IN ('schema_version', 'flyway_schema_history', 'products')
        ORDER BY tablename
    ) LOOP
        EXECUTE 'TRUNCATE TABLE ' || table_name || ' CASCADE;';
        RAISE NOTICE 'Truncated table: %', table_name;
    END LOOP;
END \$\$;
"@

# Function to clean a database
function Clean-Database {
    param(
        [string]$Container,
        [string]$Database,
        [string]$DisplayName,
        [string]$SQL,
        [ref]$TotalRef,
        [ref]$SuccessRef,
        [ref]$FailedRef
    )

    $TotalRef.Value++
    $currentTotal = $TotalRef.Value
    Write-Host "[$currentTotal] Cleaning $DisplayName..."

    try {
        $SQL | docker exec -i "$Container" psql -U username -d "$Database" 2>&1 | Out-Null

        if ($LASTEXITCODE -eq 0) {
            Write-Host "     Success - $DisplayName cleared"
            $SuccessRef.Value++
        } else {
            throw "Docker command failed with exit code $LASTEXITCODE"
        }
    } catch {
        Write-Host "     Fail - $DisplayName"
        Write-Host "     Error: $($_.Exception.Message)"
        $FailedRef.Value++
    }
    Write-Host ""
}

# Clean databases based on force flag
if ($Force) {
    # Force clean - all tables
    Clean-Database "reactiveordermicroservicearchitecture-auth-db-1" "auth_service_db" "Auth DB" $truncateAllSQL ([ref]$Total) ([ref]$Success) ([ref]$Failed)
    Clean-Database "reactiveordermicroservicearchitecture-order-db-1" "order_service_db" "Order DB" $truncateAllSQL ([ref]$Total) ([ref]$Success) ([ref]$Failed)
    Clean-Database "reactiveordermicroservicearchitecture-inventory-db-1" "inventory_service_db" "Inventory DB" $truncateAllSQL ([ref]$Total) ([ref]$Success) ([ref]$Failed)
    Clean-Database "reactiveordermicroservicearchitecture-payment-db-1" "payment_service_db" "Payment DB" $truncateAllSQL ([ref]$Total) ([ref]$Success) ([ref]$Failed)
    Clean-Database "reactiveordermicroservicearchitecture-orchestrator-db-1" "orchestrator_service_db" "Orchestrator DB" $truncateAllSQL ([ref]$Total) ([ref]$Success) ([ref]$Failed)
} else {
    # Standard cleanup - preserve reference data
    Clean-Database "reactiveordermicroservicearchitecture-auth-db-1" "auth_service_db" "Auth DB" $truncateAllSQL ([ref]$Total) ([ref]$Success) ([ref]$Failed)
    Clean-Database "reactiveordermicroservicearchitecture-order-db-1" "order_service_db" "Order DB (discounts preserved)" $truncatePreserveDiscountsSQL ([ref]$Total) ([ref]$Success) ([ref]$Failed)
    Clean-Database "reactiveordermicroservicearchitecture-inventory-db-1" "inventory_service_db" "Inventory DB (products preserved)" $truncatePreserveProductsSQL ([ref]$Total) ([ref]$Success) ([ref]$Failed)
    Clean-Database "reactiveordermicroservicearchitecture-payment-db-1" "payment_service_db" "Payment DB" $truncateAllSQL ([ref]$Total) ([ref]$Success) ([ref]$Failed)
    Clean-Database "reactiveordermicroservicearchitecture-orchestrator-db-1" "orchestrator_service_db" "Orchestrator DB" $truncateAllSQL ([ref]$Total) ([ref]$Success) ([ref]$Failed)
}

# Reset product stock to initial values
$Total++
Write-Host "[$Total] Restoring product stock..."
try {
    $resetProductStockSQL | docker exec -i "reactiveordermicroservicearchitecture-inventory-db-1" psql -U username -d "inventory_service_db" 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "     Success - Product stock restored"
        $Success++
    } else {
        throw "Docker command failed with exit code $LASTEXITCODE"
    }
} catch {
    Write-Host "     Fail - Product stock restore"
    Write-Host "     Error: $($_.Exception.Message)"
    $Failed++
}
Write-Host ""

# Summary
Write-Host "============================================================"
Write-Host "  CLEANUP SUMMARY"
Write-Host "============================================================"
Write-Host "  Total:   $Total"
Write-Host "  Success: $Success"
Write-Host "  Failed:  $Failed"
Write-Host "============================================================"

if ($Failed -gt 0) {
    Write-Host "[ERROR] Some databases failed to clean"
    exit 1
} else {
    Write-Host "✅ All databases cleaned successfully"
    exit 0
}

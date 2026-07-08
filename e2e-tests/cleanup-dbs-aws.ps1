# ============================================================
# E2E Test Database Cleanup Script (AWS RDS - PowerShell)
# ============================================================
# Requires network access to RDS (via bastion host, VPN, or SSM tunnel).
#
# Usage: .\cleanup-dbs-aws.ps1 [-Force]
#
# Required environment variables:
#   $env:AWS_RDS_HOST_AUTH, $env:AWS_RDS_HOST_ORDER, etc.
#   $env:AWS_RDS_PASSWORD_AUTH, $env:AWS_RDS_PASSWORD_ORDER, etc.
#
# Or single host: $env:AWS_RDS_HOST (if all DBs on same endpoint)
# ============================================================

param(
    [switch]$Force
)

$ErrorActionPreference = "Continue"

if ($Force) {
    Write-Host "[INFO] FORCE CLEAN enabled - ALL tables"
} else {
    Write-Host "[INFO] Standard cleanup - Preserving reference data (products, discounts)"
}

Write-Host "============================================================"
Write-Host "  E2E Test Database Cleanup (AWS RDS)"
Write-Host "============================================================"
Write-Host ""

$Total = 0
$Success = 0
$Failed = 0

# DB configurations (username matches Terraform: ${service}_admin)
$databases = @{
    auth         = @{ User = "auth_admin"; DB = "auth_db"; Host = if ($env:AWS_RDS_HOST_AUTH) { $env:AWS_RDS_HOST_AUTH } else { $env:AWS_RDS_HOST }; Password = $env:AWS_RDS_PASSWORD_AUTH }
    order        = @{ User = "order_admin"; DB = "order_db"; Host = if ($env:AWS_RDS_HOST_ORDER) { $env:AWS_RDS_HOST_ORDER } else { $env:AWS_RDS_HOST }; Password = $env:AWS_RDS_PASSWORD_ORDER }
    inventory    = @{ User = "inventory_admin"; DB = "inventory_db"; Host = if ($env:AWS_RDS_HOST_INVENTORY) { $env:AWS_RDS_HOST_INVENTORY } else { $env:AWS_RDS_HOST }; Password = $env:AWS_RDS_PASSWORD_INVENTORY }
    payment      = @{ User = "payment_admin"; DB = "payment_db"; Host = if ($env:AWS_RDS_HOST_PAYMENT) { $env:AWS_RDS_HOST_PAYMENT } else { $env:AWS_RDS_HOST }; Password = $env:AWS_RDS_PASSWORD_PAYMENT }
    orchestrator = @{ User = "orchestrator_admin"; DB = "orchestrator_db"; Host = if ($env:AWS_RDS_HOST_ORCHESTRATOR) { $env:AWS_RDS_HOST_ORCHESTRATOR } else { $env:AWS_RDS_HOST }; Password = $env:AWS_RDS_PASSWORD_ORCHESTRATOR }
}

# SQL templates
$truncateAllSQL = "DO `$`$ DECLARE t TEXT; BEGIN FOR t IN (SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('schema_version','flyway_schema_history') ORDER BY tablename) LOOP EXECUTE 'TRUNCATE TABLE ' || t || ' CASCADE;'; END LOOP; END `$`$;"
$truncatePreserveDiscountsSQL = "DO `$`$ DECLARE t TEXT; BEGIN FOR t IN (SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('schema_version','flyway_schema_history','discounts') ORDER BY tablename) LOOP EXECUTE 'TRUNCATE TABLE ' || t || ' CASCADE;'; END LOOP; END `$`$;"
$truncatePreserveProductsSQL = "DO `$`$ DECLARE t TEXT; BEGIN FOR t IN (SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('schema_version','flyway_schema_history','products') ORDER BY tablename) LOOP EXECUTE 'TRUNCATE TABLE ' || t || ' CASCADE;'; END LOOP; END `$`$;"

$resetStockSQL = @"
UPDATE products SET available_qty = CASE id
    WHEN 'a1b2c3d4-e5f6-7890-abcd-ef1234567890' THEN 100
    WHEN 'b2c3d4e5-f6a7-8901-bcde-f12345678901' THEN 50
    WHEN 'c3d4e5f6-a7b8-9012-cdef-123456789012' THEN 75
    WHEN 'd4e5f6a7-b8c9-0123-defa-234567890123' THEN 30
    WHEN 'e5f6a7b8-c9d0-1234-efab-345678901234' THEN 60
    END, reserved_qty=0, sold_qty=0, last_modified_date=NOW()
WHERE id IN ('a1b2c3d4-e5f6-7890-abcd-ef1234567890','b2c3d4e5-f6a7-8901-bcde-f12345678901','c3d4e5f6-a7b8-9012-cdef-123456789012','d4e5f6a7-b8c9-0123-defa-234567890123','e5f6a7b8-c9d0-1234-efab-345678901234');
"@

function Clean-RdsDatabase {
    param(
        [string]$Service,
        [string]$SQL,
        [string]$DisplayName
    )

    $script:Total++
    Write-Host "[$($script:Total)] Cleaning $DisplayName..."

    $config = $databases[$Service]
    if (-not $config.Host) {
        Write-Host "     Fail - No host configured (set `$env:AWS_RDS_HOST_$($Service.ToUpper()) or `$env:AWS_RDS_HOST)"
        $script:Failed++
        return
    }

    try {
        $env:PGPASSWORD = $config.Password
        $result = & psql -h $config.Host -p 5432 -U $config.User -d $config.DB -c $SQL 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "     Success - $DisplayName"
            $script:Success++
        } else {
            throw "psql failed: $result"
        }
    } catch {
        Write-Host "     Fail - $DisplayName"
        Write-Host "     Error: $($_.Exception.Message)"
        $script:Failed++
    } finally {
        $env:PGPASSWORD = ""
    }
    Write-Host ""
}

# Clean databases
if ($Force) {
    Clean-RdsDatabase "auth" $truncateAllSQL "Auth DB"
    Clean-RdsDatabase "order" $truncateAllSQL "Order DB (force)"
    Clean-RdsDatabase "inventory" $truncateAllSQL "Inventory DB (force)"
    Clean-RdsDatabase "payment" $truncateAllSQL "Payment DB"
    Clean-RdsDatabase "orchestrator" $truncateAllSQL "Orchestrator DB"
} else {
    Clean-RdsDatabase "auth" $truncateAllSQL "Auth DB"
    Clean-RdsDatabase "order" $truncatePreserveDiscountsSQL "Order DB (discounts preserved)"
    Clean-RdsDatabase "inventory" $truncatePreserveProductsSQL "Inventory DB (products preserved)"
    Clean-RdsDatabase "payment" $truncateAllSQL "Payment DB"
    Clean-RdsDatabase "orchestrator" $truncateAllSQL "Orchestrator DB"
}

# Reset product stock
$Total++
Write-Host "[$Total] Restoring product stock..."
$config = $databases["inventory"]
try {
    $env:PGPASSWORD = $config.Password
    & psql -h $config.Host -p 5432 -U $config.User -d $config.DB -c $resetStockSQL 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "     Success - Product stock restored"
        $Success++
    } else { throw "Failed" }
} catch {
    Write-Host "     Fail - Product stock restore"
    $Failed++
} finally {
    $env:PGPASSWORD = ""
}

Write-Host ""
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
    Write-Host "All databases cleaned successfully"
    exit 0
}

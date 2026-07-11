@echo off
REM ============================================================
REM E2E Test Database Cleanup Script (Docker)
REM ============================================================
REM This script clears test data from all 5 databases before E2E tests
REM Usage: cleanup-dbs.bat [--force]
REM   --force: Clean ALL tables including reference data (products, discounts)
REM
REM Preserved by default (reference data):
REM   - inventory_service_db.products
REM   - order_service_db.discounts
REM ============================================================

setlocal

set FORCE_CLEAN=0

REM Parse arguments
if "%1"=="--force" set FORCE_CLEAN=1

if %FORCE_CLEAN% equ 1 (
    echo [INFO] FORCE CLEAN enabled - ALL tables
    echo          Tables to be cleaned: ALL (including products, discounts)
) else (
    echo [INFO] Standard cleanup - Preserving reference data
    echo          Preserved: products (inventory), discounts (order)
)

echo ============================================================
echo   E2E Test Database Cleanup
echo   Target: Docker containers
echo ============================================================
echo.

set TOTAL=0
set SUCCESS=0
set FAILED=0

REM Check if force clean
if %FORCE_CLEAN% equ 1 goto :force_clean
goto :standard_clean

REM ========================================
REM Standard cleanup - preserve reference data
REM ========================================
:standard_clean

REM Auth DB
set /a TOTAL=%TOTAL%+1
echo [%TOTAL%] Cleaning Auth DB...
docker exec reactiveordermicroservicearchitecture-auth-db-1 p psql -U username -d auth_service_db -c "TRUNCATE TABLE users CASCADE;" > nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo      OK - Auth DB cleared
    set /a SUCCESS=%SUCCESS%+1
) else (
    echo      FAIL - Auth DB
    set /a FAILED=%FAILED%+1
)
echo.

REM Order DB (preserve discounts)
set /a TOTAL= %TOTAL%+1
echo [%TOTAL%] Cleaning Order DB...
docker exec reactiveordermicroservicearchitecture-order-db-1 psql -U username -d order_service_db -c "TRUNCATE TABLE order_items CASCADE; TRUNCATE TABLE order_ledger CASCADE; TRUNCATE TABLE orders CASCADE;" > nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo      OK - Order DB cleared (discounts preserved)
    set /a SUCCESS=%SUCCESS%+1
) else (
    echo      FAIL - Order DB
    set /a FAILED=%FAILED%+1
)
echo.

REM Inventory DB (preserve products)
set /a TOTAL=%TOTAL%+1
echo [%TOTAL%] Cleaning Inventory DB...
docker exec reactiveordermicroservicearchitecture-inventory-db-1 psql -U username -d inventory_service_db -c "TRUNCATE TABLE inventory CASCADE;" > nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo      OK - Inventory DB cleared (products preserved)
    set /a SUCCESS=%SUCCESS%+1
) else (
    echo      FAIL - Inventory DB
    set /a FAILED=%FAILED%+1
)
echo.

REM Payment DB
set /a TOTAL=%TOTAL%+1
echo [%TOTAL%] Cleaning Payment DB...
docker exec reactiveordermicroservicearchitecture-payment-db-1 psql -U username -d payment_service_db -c "TRUNCATE TABLE payments CASCADE;" > nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo      OK - Payment DB cleared
    set /a SUCCESS=%SUCCESS%+1
) else (
    echo      FAIL - Payment DB
    set /a FAILED=%FAILED%+1
)
echo.

REM Orchestrator DB
set /a TOTAL=%TOTAL%+1
echo [%TOTAL%] Cleaning Orchestrator DB...
docker exec reactiveordermicroservicearchitecture-orchestrator-db-1 psql -U username -d orchestrator_service_db -c "TRUNCATE TABLE saga CASCADE;" > nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo      OK - Orchestrator DB cleared
    set /a SUCCESS=%SUCCESS%+1
) else (
    echo      FAIL - Orchestrator DB
    set /audiFAILED=%FAILED%+1
)
echo.

goto :show_summary

REM ========================================
REM Force clean - all tables
REM ========================================
:force_clean

REM Auth DB
set /a TOTAL=%TOTAL%+1
echo [%TOTAL%] Cleaning Auth DB...
docker exec reactiveordermicroservicearchitecture-auth-db-1 psql -U username -d auth_service_db -c "TRUNCATE TABLE users CASCADE;" > nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo      OK - Auth DB cleared
    set /a SUCCESS=%SUCCESS%+1
) else (
    echo      FAIL - Auth DB
    set /a FAILED=%FAILED%+1
)
echo.

REM Order DB
set /a TOTAL=%TOTAL%+1
echo [%TOTAL%] Cleaning Order DB...
docker exec reactiveordermicroservicearchitecture-order-db-1 psql -U username -d order_service_db -c "TRUNCATE TABLE order_items CASCADE; TRUNCATE TABLE order_ledger CASCADE; TRUNCATE TABLE orders CASCADE; TRUNCATE TABLE discounts CASCADE;" > nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo      OKOK - Order DB cleared
    set /a SUCCESS=%SUCCESS%+1
) else (
    echo      FAIL - Order DB
    set /a FAILED=%FAILED%+1
)
echo.

REM Inventory DB
set /a TOTAL=%TOTAL%+1
echo [%TOTAL%] Cleaning Inventory DB...
docker exec reactiveordermicroservicearchitecture-inventory-db-1 psql -U username -d inventory_service_db -c "TRUNCATE TABLE inventory CASCADE; TRUNCATE TABLE products CASCADE;" > nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo      OK - Inventory DB cleared
    set /a SUCCESS=%SUCCESS%+1
) else (
    echo      FAIL - Inventory DB
    set /a FAILED=%FAILED%+1
)
echo.

REM Payment DB
set /a TOTAL=%TOTAL%+1
echo [%TOTAL%] Cleaning Payment DB...
docker exec reactiveordermicroservicearchitecture-payment-db-1 psql -U username -d payment_service_db -c "TRUNCATE TABLE payments CASCADE;" > nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo      OK - Payment DB cleared
    set /a SUCCESS=%SUCCESS%+1
) else (
    echo      FAIL - Payment DB
    set /a FAILED=%FAILED%+1
)
echo.

REM Orchestrator DB
set /a TOTAL=%TOTAL%+1
echo [%TOTAL%] Cleaning Orchestrator DB...
docker exec reactiveordermicroservicearchitecture-orchestrator-db-1 psql -U username -d orchestrator_service_db -c "TRUNCATE TABLE saga CASCADE;" > nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo      OK - Orchestrator DB cleared
    set /a SUCCESS=%SUCCESS%+1
) else (
    echo      FAIL - Orchestrator DB
    set /a FAILED=%FAILED%+1
)
echo.

REM ========================================
REM Summary
REM ========================================
:show_summary
echo ============================================================
echo   CLEANUP SUMMARY
echo ============================================================
echo   Total:   %TOTAL%
echo   Success: %SUCCESS%
echo   Failed:  %FAILED%
echo ============================================================

if %FAILED% gtr 0 (
    echo [ERROR] Some databases failed to clean
    exit /b 1
) else (
    echo [SUCCESS] All databases cleaned successfully
    exit /b 0
)

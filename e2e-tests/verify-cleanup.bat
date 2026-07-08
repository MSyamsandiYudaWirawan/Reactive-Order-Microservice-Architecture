@echo off
REM ============================================================
REM Verify Cleanup Script - Check preserved reference data
REM ============================================================
REM This script verifies that cleanup preserves reference data
REM and cleans test data properly
REM ============================================================

echo ============================================================
echo   Verify Cleanup Script
echo ============================================================
echo.

set DB_HOST=localhost
set DB_USER=username
set DB_PASSWORD=password

set PGPASSWORD=%DB_PASSWORD%

echo Checking reference data preservation...
echo.

REM Check products table in inventory service
echo 📦 Products in inventory_service_db:
echo ------------------------------------------------------------
psql -h %DB_HOST% -p 5434 -U %DB_USER% -d inventory_service_db -c "SELECT id, name, price, available_qty FROM products LIMIT 5;" 2>nul
if %ERRORLEVEL% neq 0 echo ❌ Failed to query products table

echo.
echo 🏷️  Discounts in order_service_db:
echo ------------------------------------------------------------
psql -h %DB_HOST% -p 5433 -U %DB_USER% -d order_service_db -c "SELECT code, discount_type, value FROM discounts LIMIT 5;" 2>nul
if %ERRORLEVEL% neq 0 echo ❌ Failed to query discounts table

echo.
echo 📋 Sample test data counts (should be 0 after cleanup):
echo ------------------------------------------------------------

echo Users (auth_service_db):
psql -h %DB_HOST% -p 5432 -U %DB_USER% -d auth_service_db -c "SELECT COUNT(*) as user_count FROM users WHERE email LIKE '%%testmail.com';" 2>nul

echo.
echo Orders (order_service_db):
psql -h %DB_HOST% -p 5433 -U %DB_USER% -d order_service_db -c "SELECT COUNT(*) as order_count FROM orders;" 2>nul

echo.
echo Payments (payment_service_db):
psql -h %DB_HOST% -p 5435 -U %DB_USER% -d payment_service_db -c "SELECT COUNT(*) as payment_count FROM payments;" 2>nul

echo.
echo Stock reservations (inventory_service_db):
psql -h %DB_HOST% -p 5434 -U %DB_USER% -d inventory_service_db -c "SELECT COUNT(*) as reservation_count FROM stock_reservation;" 2>nul

echo.
echo Saga states (orchestrator_service_db):
psql -h %DB_HOST% -p 5436 -U %DB_USER% -d orchestrator_service_db -c "SELECT COUNT(*) as saga_count FROM saga_state;" 2>nul

echo.
echo ============================================================
echo   Verification Complete
echo ============================================================
echo.
echo ✅ Reference data (products, discounts) should be present
echo ✅ Test data (orders, payments, users, etc.) should be 0

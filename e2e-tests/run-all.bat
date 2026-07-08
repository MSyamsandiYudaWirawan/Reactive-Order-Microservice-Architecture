@echo off
REM ============================================================
REM E2E Test Runner - Newman (Postman CLI)
REM ============================================================
REM Usage:
REM   run-all.bat                              (fast tests only)
REM   run-all.bat --with-scheduler             (include scheduler/compensation tests)
REM   run-all.bat http://your-alb-url 80       (AWS ALB)
REM   run-all.bat http://localhost 8080 3000 --with-scheduler
REM ============================================================

setlocal enabledelayedexpansion

set WITH_SCHEDULER=0
set BASE_URL=http://localhost
set GATEWAY_PORT=8080
set DELAY_MS=3000

REM Parse arguments
for %%a in (%*) do (
    if "%%a"=="--with-scheduler" (
        set WITH_SCHEDULER=1
    )
)

if not "%1"=="" if not "%1"=="--with-scheduler" set BASE_URL=%1
if not "%2"=="" if not "%2"=="--with-scheduler" set GATEWAY_PORT=%2
if not "%3"=="" if not "%3"=="--with-scheduler" set DELAY_MS=%3

echo.
echo ============================================================
echo   Reactive Order Microservice - E2E Tests
echo   Target: %BASE_URL%:%GATEWAY_PORT%
echo   Delay: %DELAY_MS%ms (fast) / 15000ms (scheduler)
if %WITH_SCHEDULER% equ 1 (echo   Scheduler tests: ENABLED)
echo ============================================================
echo.

REM Check if newman is installed
where newman >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Newman is not installed. Install with:
    echo   npm install -g newman
    exit /b 1
)

set SCRIPT_DIR=%~dp0
set ENV_FILE=%SCRIPT_DIR%environment.json
set PASSED=0
set FAILED=0
set TOTAL=0

REM Generate environment
echo {"id":"e2e-env","name":"E2E Test Environment","values":[{"key":"base_url","value":"%BASE_URL%","enabled":true},{"key":"gateway_port","value":"%GATEWAY_PORT%","enabled":true},{"key":"test_email","value":"","enabled":true},{"key":"test_password","value":"TestUser_1!","enabled":true},{"key":"test_name","value":"E2E Test User","enabled":true},{"key":"access_token","value":"","enabled":true},{"key":"refresh_token","value":"","enabled":true},{"key":"transaction_id","value":"","enabled":true},{"key":"payment_id","value":"","enabled":true},{"key":"product_id","value":"d4e5f6a7-b8c9-0123-defa-234567890123","enabled":true}]} > "%ENV_FILE%"

echo.
echo ============================
echo   FAST TESTS (instant flow)
echo ============================

REM ---- Auth Flow ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Auth Flow
echo ------------------------------------------------------------
newman run "%SCRIPT_DIR%auth-flow.postman_collection.json" -e "%ENV_FILE%" --delay-request %DELAY_MS% --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Happy Path ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Happy Path (Order COMPLETED)
echo ------------------------------------------------------------
newman run "%SCRIPT_DIR%happy-path.postman_collection.json" -e "%ENV_FILE%" --delay-request %DELAY_MS% --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Idempotency ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Idempotency
echo ------------------------------------------------------------
newman run "%SCRIPT_DIR%idempotency-test.postman_collection.json" -e "%ENV_FILE%" --delay-request %DELAY_MS% --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

echo.
echo ====================================
echo   COMPENSATION TESTS (instant flow)
echo ====================================

REM ---- Out of Stock ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Compensation: Out of Stock (no payment needed)
echo ------------------------------------------------------------
newman run "%SCRIPT_DIR%out-of-stock.postman_collection.json" -e "%ENV_FILE%" --delay-request %DELAY_MS% --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Payment Failed + Retry ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Compensation: Payment Failed + Retry
echo ------------------------------------------------------------
newman run "%SCRIPT_DIR%payment-failed-retry.postman_collection.json" -e "%ENV_FILE%" --delay-request %DELAY_MS% --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Refund Flow ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Compensation: Refund (stock exhaustion)
echo ------------------------------------------------------------
newman run "%SCRIPT_DIR%refund-flow.postman_collection.json" -e "%ENV_FILE%" --delay-request %DELAY_MS% --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Scheduler Tests ----
if %WITH_SCHEDULER% equ 1 (
    echo.
    echo ============================================
    echo   COMPENSATION TESTS (scheduler-dependent)
    echo   Polling every 15s, payment expiry=30s,
    echo   order expiry=90s, scheduler=every 30s
    echo ============================================

    set /a TOTAL+=1
    echo.
    echo [!TOTAL!] Compensation: Order Expiry (never paid, ~2min)
    echo ------------------------------------------------------------
    newman run "%SCRIPT_DIR%order-expiry.postman_collection.json" -e "%ENV_FILE%" --delay-request 15000 --timeout-request 60000
    if !ERRORLEVEL! equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

    set /a TOTAL+=1
    echo.
    echo [!TOTAL!] Compensation: Payment Expiry (no webhook, ~2min)
    echo ------------------------------------------------------------
    newman run "%SCRIPT_DIR%payment-expiry.postman_collection.json" -e "%ENV_FILE%" --delay-request 15000 --timeout-request 60000
    if !ERRORLEVEL! equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)
)

REM ---- Summary ----
echo.
echo ============================================================
echo   TEST SUMMARY
echo ============================================================
echo   Total:  %TOTAL%
echo   Passed: %PASSED%
echo   Failed: %FAILED%
echo ============================================================

if %FAILED% gtr 0 (
    echo   SOME TESTS FAILED
    exit /b 1
) else (
    echo   ALL TESTS PASSED
    exit /b 0
)

@echo off
REM ============================================================
REM E2E Test Runner - Newman (Postman CLI)
REM ============================================================
REM Usage:
REM   run-all.bat                              (fast tests only)
REM   run-all.bat --with-scheduler             (include scheduler/compensation tests)
REM   run-all.bat http://your-alb-url 80       (AWS ALB)
REM ============================================================

setlocal

set WITH_SCHEDULER=0
set BASE_URL=http://localhost
set GATEWAY_PORT=8080
set DELAY_MS=2000
set POLL_DELAY_MS=5000

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
echo   Delay: %DELAY_MS%ms (request) / %POLL_DELAY_MS%ms (poll) / 15000ms (scheduler)
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

echo.
echo ============================
echo   FAST TESTS (instant flow)
echo ============================

REM ---- Happy Path ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Happy Path (Order COMPLETED)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%happy-path.postman_collection.json" -e "%ENV_FILE%" --delay-request %POLL_DELAY_MS% --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Payment Failed + Retry ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Payment Failed + Retry (COMPLETED)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%payment-failed-retry.postman_collection.json" -e "%ENV_FILE%" --delay-request %POLL_DELAY_MS% --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Refund Flow (Out of Stock After Payment) ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Out of Stock After Payment (REFUNDED)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%refund-flow.postman_collection.json" -e "%ENV_FILE%" --delay-request %POLL_DELAY_MS% --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Out of Stock (no payment) ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Out of Stock - No Payment (OUT_OF_STOCK)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%out-of-stock.postman_collection.json" -e "%ENV_FILE%" --delay-request %POLL_DELAY_MS% --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Refund Flow (Out of Stock + Paid → REFUNDED) ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Refund Flow: Out of Stock + Paid (REFUNDED)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%refund-flow.postman_collection.json" -e "%ENV_FILE%" --delay-request %POLL_DELAY_MS% --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Refund Failed + DLQ ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Refund Failed + DLQ (manual intervention)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%refund-failed-dlq.postman_collection.json" -e "%ENV_FILE%" --delay-request %POLL_DELAY_MS% --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Verify DLQ message in Kafka ----
echo.
echo   Checking payment-dlq topic...
docker exec reactiveordermicroservicearchitecture-kafka-1 kafka-console-consumer --bootstrap-server localhost:29092 --topic payment-dlq --from-beginning --timeout-ms 5000 2>nul | findstr "PROVIDER_ERROR" >nul
if %ERRORLEVEL% equ 0 (
    echo   ✅ DLQ message found in payment-dlq topic
) else (
    echo   ❌ DLQ message NOT found in payment-dlq topic
    set /a FAILED+=1
    set /a TOTAL+=1
)

REM ---- Scheduler Tests ----
if %WITH_SCHEDULER% equ 0 goto :summary

echo.
echo ============================================
echo   SCHEDULER TESTS (requires waiting)
echo   Payment expiry=30s, Order expiry=90s
echo ============================================

REM ---- Order Expiry (Never Paid) ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Order Expiry - Never Paid (~2min)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%order-expiry.postman_collection.json" -e "%ENV_FILE%" --delay-request 15000 --timeout-request 60000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Payment Expiry (No Webhook) ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Payment Expiry - No Webhook (~2min)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%payment-expiry.postman_collection.json" -e "%ENV_FILE%" --delay-request 15000 --timeout-request 60000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Late Webhook After Payment Expired ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Late Webhook After Payment Expired (~2min)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%late-webhook.postman_collection.json" -e "%ENV_FILE%" --delay-request 15000 --timeout-request 60000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

:summary
REM ---- Summary ----
goto :show_summary

:reset_env
(
echo {
echo   "id": "e2e-env",
echo   "name": "E2E Test Environment",
echo   "values": [
echo     {"key": "base_url", "value": "%BASE_URL%", "enabled": true},
echo     {"key": "gateway_port", "value": "%GATEWAY_PORT%", "enabled": true},
echo     {"key": "test_email", "value": "", "enabled": true},
echo     {"key": "test_phone", "value": "", "enabled": true},
echo     {"key": "test_password", "value": "Syamsandi_1!", "enabled": true},
echo     {"key": "test_name", "value": "Syamsandi", "enabled": true},
echo     {"key": "access_token", "value": "", "enabled": true},
echo     {"key": "refresh_token", "value": "", "enabled": true},
echo     {"key": "transaction_id", "value": "", "enabled": true},
echo     {"key": "payment_id", "value": "", "enabled": true},
echo     {"key": "product_id", "value": "d4e5f6a7-b8c9-0123-defa-234567890123", "enabled": true}
echo   ]
echo }
) > "%ENV_FILE%"
goto :eof

:show_summary
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

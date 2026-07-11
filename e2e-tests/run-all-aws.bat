@echo off
REM ============================================================
REM E2E Test Runner for AWS (Newman / Postman CLI) - Windows
REM ============================================================
REM Runs all E2E tests against the AWS ECS Fargate deployment.
REM
REM Usage:
REM   run-all-aws.bat <ALB_DNS>
REM   run-all-aws.bat <ALB_DNS> --with-scheduler
REM   run-all-aws.bat <ALB_DNS> --with-cleanup
REM   run-all-aws.bat <ALB_DNS> --with-scheduler --with-cleanup
REM
REM Examples:
REM   run-all-aws.bat reactive-order-alb-123456.ap-southeast-3.elb.amazonaws.com
REM
REM Get ALB DNS:
REM   cd terraform && terraform output alb_dns_name
REM ============================================================

setlocal enabledelayedexpansion

set WITH_SCHEDULER=0
set WITH_CLEANUP=0
set ALB_DNS=

REM Parse arguments
for %%a in (%*) do (
    if "%%a"=="--with-scheduler" (
        set WITH_SCHEDULER=1
    ) else if "%%a"=="--with-cleanup" (
        set WITH_CLEANUP=1
    ) else (
        set ALB_DNS=%%a
    )
)

if "%ALB_DNS%"=="" (
    echo [ERROR] ALB DNS name is required.
    echo.
    echo Usage: run-all-aws.bat ^<ALB_DNS^> [--with-scheduler] [--with-cleanup]
    echo.
    echo Get ALB DNS from Terraform:
    echo   cd terraform ^&^& terraform output alb_dns_name
    exit /b 1
)

set BASE_URL=http://%ALB_DNS%
set GATEWAY_PORT=80
set POLL_DELAY_MS=8000
set SCHEDULER_DELAY_MS=15000

echo.
echo ============================================================
echo   Reactive Order Microservice - E2E Tests (AWS)
echo   Target: %BASE_URL%:%GATEWAY_PORT%
echo   Delay: %POLL_DELAY_MS%ms (poll) / %SCHEDULER_DELAY_MS%ms (scheduler)
if %WITH_SCHEDULER% equ 1 echo   Scheduler tests: ENABLED
if %WITH_CLEANUP% equ 1 echo   DB Cleanup: ENABLED
echo ============================================================
echo.

REM Check newman
where newman >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Newman is not installed. Install with:
    echo   npm install -g newman
    exit /b 1
)

set SCRIPT_DIR=%~dp0
set ENV_FILE=%SCRIPT_DIR%environment-aws.json

REM Optional: cleanup databases
if %WITH_CLEANUP% equ 1 (
    echo [1/2] Cleaning AWS RDS databases...
    powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%cleanup-dbs-aws.ps1"
    echo.
)

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

REM ---- Out of Stock (no payment) ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Out of Stock - No Payment (OUT_OF_STOCK)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%out-of-stock.postman_collection.json" -e "%ENV_FILE%" --delay-request %POLL_DELAY_MS% --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Refund Flow ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Refund Flow: Out of Stock + Paid (REFUNDED)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%refund-flow.postman_collection.json" -e "%ENV_FILE%" --delay-request 3000 --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Refund Failed + DLQ ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Refund Failed + DLQ (manual intervention)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%refund-failed-dlq.postman_collection.json" -e "%ENV_FILE%" --delay-request 3000 --timeout-request 30000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Scheduler Tests ----
if %WITH_SCHEDULER% equ 0 goto :summary

echo.
echo ============================================
echo   SCHEDULER TESTS (requires waiting)
echo   Payment expiry=30s, Order expiry=90s
echo ============================================

REM ---- Order Expiry ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Order Expiry - Never Paid (~2min)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%order-expiry.postman_collection.json" -e "%ENV_FILE%" --delay-request %SCHEDULER_DELAY_MS% --timeout-request 60000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Payment Expiry ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Payment Expiry - No Webhook (~2min)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%payment-expiry.postman_collection.json" -e "%ENV_FILE%" --delay-request %SCHEDULER_DELAY_MS% --timeout-request 60000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

REM ---- Late Webhook ----
set /a TOTAL+=1
echo.
echo [%TOTAL%] Late Webhook After Payment Expired (~2min)
echo ------------------------------------------------------------
call :reset_env
call newman run "%SCRIPT_DIR%late-webhook.postman_collection.json" -e "%ENV_FILE%" --delay-request %SCHEDULER_DELAY_MS% --timeout-request 60000
if %ERRORLEVEL% equ 0 (set /a PASSED+=1) else (set /a FAILED+=1)

:summary
echo.
echo ============================================================
echo   TEST SUMMARY (AWS)
echo ============================================================
echo   Target: %BASE_URL%:%GATEWAY_PORT%
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

:reset_env
(
echo {
echo   "id": "e2e-env-aws",
echo   "name": "E2E Test Environment (AWS)",
echo   "values": [
echo     {"key": "base_url", "value": "%BASE_URL%", "enabled": true},
echo     {"key": "gateway_port", "value": "%GATEWAY_PORT%", "enabled": true},
echo     {"key": "test_email", "value": "", "enabled": true},
echo     {"key": "test_phone", "value": "", "enabled": true},
echo     {"key": "test_password", "value": "Syamsandi_1^!", "enabled": true},
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

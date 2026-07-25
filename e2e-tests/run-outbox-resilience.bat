@echo off
REM ============================================================
REM Outbox Resilience Test
REM Proves Debezium replays WAL after kafka-connect restart
REM
REM Flow:
REM   part1 → docker stop → part2 (assert PENDING) → docker start → wait → part3 (COMPLETED)
REM
REM Usage: run-outbox-resilience.bat [base_url] [gateway_port]
REM ============================================================

setlocal

set BASE_URL=http://localhost
set GATEWAY_PORT=8080
set SCRIPT_DIR=%~dp0

if not "%1"=="" set BASE_URL=%1
if not "%2"=="" set GATEWAY_PORT=%2

REM Check newman
where newman >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Newman not installed. Run: npm install -g newman
    exit /b 1
)

REM Detect kafka-connect container name
for /f "tokens=*" %%i in ('docker ps --format "{{.Names}}" ^| findstr "kafka-connect"') do set CONNECT_CONTAINER=%%i
if "%CONNECT_CONTAINER%"=="" (
    echo [ERROR] No running kafka-connect container found.
    exit /b 1
)

echo.
echo ============================================================
echo   Outbox Resilience Test
echo   Target: %BASE_URL%:%GATEWAY_PORT%
echo   kafka-connect container: %CONNECT_CONTAINER%
echo ============================================================

REM Write env file — done here (no enabledelayedexpansion) so ! in password is safe
set ENV_FILE=%SCRIPT_DIR%environment-outbox-run.json
(
echo {
echo   "id": "e2e-outbox-env",
echo   "name": "E2E Outbox Resilience Environment",
echo   "values": [
echo     {"key": "base_url", "value": "%BASE_URL%", "enabled": true},
echo     {"key": "gateway_port", "value": "%GATEWAY_PORT%", "enabled": true},
echo     {"key": "test_email", "value": "", "enabled": true},
echo     {"key": "test_phone", "value": "", "enabled": true},
echo     {"key": "test_password", "value": "Syamsandi_1!", "enabled": true},
echo     {"key": "test_name", "value": "Syamsandi", "enabled": true},
echo     {"key": "access_token", "value": "", "enabled": true},
echo     {"key": "transaction_id", "value": "", "enabled": true},
echo     {"key": "payment_id", "value": "", "enabled": true},
echo     {"key": "product_id", "value": "d4e5f6a7-b8c9-0123-defa-234567890123", "enabled": true}
echo   ]
echo }
) > "%ENV_FILE%"

REM ---- PART 1: Register → Login → Create Order ----
echo.
echo [1/3] Register + Login + Create Order...
cmd /c newman run "%SCRIPT_DIR%outbox-resilience-part1.postman_collection.json" -e "%ENV_FILE%" --export-environment "%ENV_FILE%" --delay-request 500 --timeout-request 30000
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Part 1 failed.
    goto :cleanup_fail
)

REM ---- KILL kafka-connect ----
echo.
echo [KILL] Stopping kafka-connect: %CONNECT_CONTAINER%
docker stop %CONNECT_CONTAINER%
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Failed to stop kafka-connect container.
    goto :cleanup_fail
)
echo [KILL] kafka-connect stopped.

REM ---- PART 2: Assert order still PENDING ----
echo.
echo [2/3] Asserting order is PENDING while kafka-connect is DOWN...
cmd /c newman run "%SCRIPT_DIR%outbox-resilience-part2.postman_collection.json" -e "%ENV_FILE%" --delay-request 500 --timeout-request 30000
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Part 2 failed — order was not PENDING as expected.
    docker start %CONNECT_CONTAINER% >nul 2>nul
    goto :cleanup_fail
)

REM ---- RESTART kafka-connect ----
echo.
echo [START] Restarting kafka-connect: %CONNECT_CONTAINER%
docker start %CONNECT_CONTAINER%
echo [START] Waiting 30s for Debezium to resume from WAL offset...
timeout /t 30 /nobreak

REM ---- PART 3: Poll WAITING_PAYMENT → Complete payment → COMPLETED ----
echo.
echo [3/3] Polling for WAITING_PAYMENT recovery, then completing payment...
cmd /c newman run "%SCRIPT_DIR%outbox-resilience-part3.postman_collection.json" -e "%ENV_FILE%" --delay-request 500 --timeout-request 180000
if %ERRORLEVEL% neq 0 goto :cleanup_fail

del "%ENV_FILE%" >nul 2>nul
docker start %CONNECT_CONTAINER% >nul 2>nul
echo.
echo ✅ OUTBOX RESILIENCE TEST PASSED
exit /b 0

:cleanup_fail
del "%ENV_FILE%" >nul 2>nul
docker start %CONNECT_CONTAINER% >nul 2>nul
echo.
echo ❌ OUTBOX RESILIENCE TEST FAILED
exit /b 1

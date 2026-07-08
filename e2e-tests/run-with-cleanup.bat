@echo off
REM ============================================================
REM E2E Test Runner with Automatic Database Cleanup
REM ============================================================
REM This script is a shortcut that:
REM   1. Cleans all databases
REM   2. Runs all E2E tests
REM Usage:
REM   run-with-cleanup.bat                        (fast tests only)
REM   run-with-cleanup.bat --with-scheduler        (include scheduler tests)
REM   run-with-cleanup.bat http://your-alb-url 80  (AWS ALB)
REM ============================================================

setlocal

echo ============================================================
echo   E2E Test Runner with Database Cleanup
echo ============================================================
echo.

echo [1/2] Cleaning databases...
powershell -ExecutionPolicy Bypass -File "%~dp0cleanup-dbs.ps1"

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Database cleanup failed. Aborting tests.
    exit /b 1
)

echo.
echo [2/2] Running E2E tests...
call "%~dp0run-all.bat" %*

exit /b %ERRORLEVEL%

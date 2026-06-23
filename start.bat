@echo off
echo === Building all services ===
call mvn clean package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo Build failed!
    exit /b 1
)

echo === Starting all containers ===
docker compose -f docker-compose.full.yml up --build -d

echo === Done! Gateway available at http://localhost:8080 ===

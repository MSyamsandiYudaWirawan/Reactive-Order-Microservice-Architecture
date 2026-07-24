@echo off
echo === Building all services ===
call mvn clean package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo Build failed!
    exit /b 1
)

echo === Starting all containers ===
docker compose -f docker-compose.full.yml up --build -d

echo === Waiting for kafka-connect to be healthy ===
:wait_loop
docker inspect --format="{{.State.Health.Status}}" reactive-order-microservice-architecture-kafka-connect-1 2>nul | findstr /i "healthy" >nul
if %ERRORLEVEL% neq 0 (
    timeout /t 5 /nobreak >nul
    goto wait_loop
)

echo === Registering Debezium connectors ===
bash infra/debezium/register-connectors.sh

echo === Done! Gateway available at http://localhost:8080 ===

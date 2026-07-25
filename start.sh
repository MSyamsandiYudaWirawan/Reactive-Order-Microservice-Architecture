#!/bin/bash
set -e

echo "=== Building all services ==="
mvn clean package -DskipTests

echo "=== Starting all containers ==="
docker compose -f docker-compose.full.yml up --build -d

echo "=== Waiting 30s for containers to initialize ==="
sleep 30

echo "=== Registering Debezium connectors ==="
bash infra/debezium/register-connectors.sh

echo "=== Done! Gateway available at http://localhost:8080 ==="

#!/bin/bash
set -e

echo "=== Building all services ==="
./mvnw clean package -DskipTests

echo "=== Starting all containers ==="
docker compose -f docker-compose.full.yml up --build -d

echo "=== Done! Gateway available at http://localhost:8080 ==="

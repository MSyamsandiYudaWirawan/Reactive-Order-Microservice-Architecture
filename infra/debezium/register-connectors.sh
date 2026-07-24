#!/bin/bash

# Kafka Connect REST API endpoint (mapped to 8086 on host to avoid conflict with inventory-service:8083)
CONNECT_URL="http://localhost:8086/connectors"

# Wait for Kafka Connect to be ready before registering connectors
echo "Waiting for Kafka Connect to be ready..."
until curl -s -o /dev/null -w "%{http_code}" "$CONNECT_URL" | grep -q "200"; do
  sleep 2
done
echo "Kafka Connect is ready."

# Registers a connector only if it doesn't already exist (idempotent)
register() {
  local name=$1
  local config=$2

  # Check if connector already registered — 200 means exists, skip
  existing=$(curl -s -o /dev/null -w "%{http_code}" "$CONNECT_URL/$name")
  if [ "$existing" = "200" ]; then
    echo "[$name] already registered, skipping"
    return
  fi

  echo "[$name] registering..."
  response=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$CONNECT_URL" \
    -H "Content-Type: application/json" \
    -d "$config")
  if [ "$response" = "201" ]; then
    echo "[$name] registered successfully"
  else
    echo "[$name] registration failed (HTTP $response)"
  fi
}

# ============ ORDER SERVICE ============
# Watches order-db.public.outbox via PostgreSQL WAL
# Routes events to Kafka topics based on aggregate_type column value
register "order-service-outbox-connector" '{
  "name": "order-service-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "order-db",
    "database.port": "5432",
    "database.user": "username",
    "database.password": "password",
    "database.dbname": "order_service_db",
    "topic.prefix": "order-service",
    "plugin.name": "pgoutput",
    "slot.name": "order_service_slot",
    "table.include.list": "public.outbox",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "aggregate_type",
    "transforms.outbox.route.topic.replacement": "${routedByValue}",
    "tombstones.on.delete": "false",
    "value.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter.schemas.enable": "false"
  }
}'

# ============ INVENTORY SERVICE ============
# Watches inventory-db.public.outbox via PostgreSQL WAL
# Routes STOCK_RESERVED / OUT_OF_STOCK events to their respective Kafka topics
register "inventory-service-outbox-connector" '{
  "name": "inventory-service-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "inventory-db",
    "database.port": "5432",
    "database.user": "username",
    "database.password": "password",
    "database.dbname": "inventory_service_db",
    "topic.prefix": "inventory-service",
    "plugin.name": "pgoutput",
    "slot.name": "inventory_service_slot",
    "table.include.list": "public.outbox",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "aggregate_type",
    "transforms.outbox.route.topic.replacement": "${routedByValue}",
    "tombstones.on.delete": "false",
    "value.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter.schemas.enable": "false"
  }
}'

# ============ PAYMENT SERVICE ============
# Watches payment-db.public.outbox via PostgreSQL WAL
# Routes PAYMENT_COMPLETED / PAYMENT_FAILED / refund events to their respective Kafka topics
register "payment-service-outbox-connector" '{
  "name": "payment-service-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "payment-db",
    "database.port": "5432",
    "database.user": "username",
    "database.password": "password",
    "database.dbname": "payment_service_db",
    "topic.prefix": "payment-service",
    "plugin.name": "pgoutput",
    "slot.name": "payment_service_slot",
    "table.include.list": "public.outbox",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "aggregate_type",
    "transforms.outbox.route.topic.replacement": "${routedByValue}",
    "tombstones.on.delete": "false",
    "value.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter.schemas.enable": "false"
  }
}'

# ============ ORCHESTRATOR SERVICE ============
# Watches orchestrator-db.public.outbox via PostgreSQL WAL
# Routes ORDER_COMPLETED / RELEASE_STOCK / REFUND_REQUESTED events to their respective Kafka topics
register "orchestrator-service-outbox-connector" '{
  "name": "orchestrator-service-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "orchestrator-db",
    "database.port": "5432",
    "database.user": "username",
    "database.password": "password",
    "database.dbname": "orchestrator_service_db",
    "topic.prefix": "orchestrator-service",
    "plugin.name": "pgoutput",
    "slot.name": "orchestrator_service_slot",
    "table.include.list": "public.outbox",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "aggregate_type",
    "transforms.outbox.route.topic.replacement": "${routedByValue}",
    "tombstones.on.delete": "false",
    "value.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter.schemas.enable": "false"
  }
}'

echo "Done."

# transforms.outbox.route.by.field": "aggregate_type",
# ↑ Read the aggregate_type column value to decide which Kafka topic to publish to

# transforms.outbox.route.topic.replacement": "${routedByValue}",
# ↑ Use that value AS the topic name directly
# e.g. aggregate_type = 'reserve-stock' → publishes to 'reserve-stock' Kafka topic

# tombstones.on.delete": "false"
# ↑ When outbox row is deleted (cleanup), don't publish a null/tombstone message to Kafka
# Without this, every delete would produce noise in your topics
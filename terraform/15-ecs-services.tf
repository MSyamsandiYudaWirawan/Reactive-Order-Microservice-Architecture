locals {
  services = {
    auth = {
      port      = 8081
      has_db    = true
      has_kafka = false
      has_redis = false
      jwt_keys  = "both" # private + public
    }
    gateway = {
      port      = 8080
      has_db    = false
      has_kafka = false
      has_redis = true
      jwt_keys  = "public"
    }
    order = {
      port      = 8082
      has_db    = true
      has_kafka = true
      has_redis = true
      jwt_keys  = "public"
    }
    inventory = {
      port      = 8083
      has_db    = true
      has_kafka = true
      has_redis = true
      jwt_keys  = "none" # internal only, no JWT validation
    }
    payment = {
      port      = 8084
      has_db    = true
      has_kafka = true
      has_redis = true
      jwt_keys  = "public"
    }
    orchestrator = {
      port      = 8085
      has_db    = true
      has_kafka = true
      has_redis = true
      jwt_keys  = "none" # internal only
    }
  }
}
# ===== Task Definitions =====
# Defines what container to run, CPU/memory, env vars, secrets
resource "aws_ecs_task_definition" "services" {
  for_each = local.services

  family                   = "reactive-order-${each.key}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name      = each.key
    image     = "${aws_ecr_repository.services[each.key].repository_url}:latest"
    essential = true

    portMappings = [
      {
        name          = each.key
        containerPort = each.value.port
        protocol      = "tcp"
      }
    ]
    environment = concat(
      # Base env vars
      [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" }
      ],
      # DB env vars (if service has a database)
      each.value.has_db ? [
        { name = "DB_URL", value = aws_db_instance.services[each.key].address },
        { name = "DB_PORT", value = tostring(aws_db_instance.services[each.key].port) },
        { name = "DB_NAME", value = local.databases[each.key].name },
      ] : [],
      # Kafka env vars
      each.value.has_kafka ? [
        { name = "KAFKA_BOOTSTRAP_SERVERS", value = aws_msk_cluster.main.bootstrap_brokers },
      ] : [],
      # Redis env vars
      each.value.has_redis ? [
        { name = "SPRING_DATA_REDIS_HOST", value = aws_elasticache_cluster.redis.cache_nodes[0].address },
        { name = "SPRING_DATA_REDIS_PORT", value = "6379" },
      ] : [],
      # Gateway-specific: service URLs via Service Connect
      each.key == "gateway" ? [
        { name = "AUTH_SERVICE_URL", value = "http://auth:8081" },
        { name = "ORDER_SERVICE_URL", value = "http://order:8082" },
        { name = "PAYMENT_SERVICE_URL", value = "http://payment:8084" },
      ] : [],
      # Order-service needs to call inventory-service
      each.key == "order" ? [
        { name = "APP_INVENTORY_SERVICE_URL", value = "http://inventory:8083" },
      ] : [],
      # Payment-service needs to call order-service
      each.key == "payment" ? [
        { name = "APP_ORDER_SERVICE_URL", value = "http://order:8082" },
      ] : []
    )

    secrets = concat(
      # JWT private key (auth-service only)
      each.value.jwt_keys == "both" ? [
        { name = "JWT_PRIVATE_KEY", valueFrom = aws_secretsmanager_secret.jwt_private_key.arn },
      ] : [],
      # JWT public key (auth, gateway, order, payment)
      contains(["both", "public"], each.value.jwt_keys) ? [
        { name = "JWT_PUBLIC_KEY", valueFrom = aws_secretsmanager_secret.jwt_public_key.arn },
      ] : [],
      # DB credentials
      each.value.has_db ? [
        { name = "DB_USERNAME", valueFrom = "${aws_secretsmanager_secret.db_credentials[each.key].arn}:username::" },
        { name = "DB_PASSWORD", valueFrom = "${aws_secretsmanager_secret.db_credentials[each.key].arn}:password::" },
      ] : []
    )

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.service[each.key].name
        "awslogs-region"        = "ap-southeast-3"
        "awslogs-stream-prefix" = each.key
      }
    }
    }]
  )

  tags = {
    Name    = "reactive-order-${each.key}"
    Service = each.key
  }
}

# ===== ECS Services =====
# Runs the task, connects to Service Connect, registers with ALB (gateway only)

resource "aws_ecs_service" "services" {
  for_each = local.services

  name            = each.key
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.services[each.key].arn
  desired_count   = 1
  launch_type     = "FARGATE"

  # Gateway takes ~90s to start on Fargate (0.25 vCPU) — don't kill it during startup
  health_check_grace_period_seconds = each.key == "gateway" ? 120 : 0

  network_configuration {
    subnets          = [aws_subnet.private-ap-southeast-3a.id]
    security_groups  = each.key == "gateway" ? [aws_security_group.gateway.id] : [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  # Service Connect — enables internal DNS (e.g. http://auth:8081)
  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn

    service {
      port_name = each.key
      client_alias {
        port     = each.value.port
        dns_name = each.key
      }
    }
  }

  # Only gateway-service registers with ALB
  dynamic "load_balancer" {
    for_each = each.key == "gateway" ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.gateway.arn
      container_name   = each.key
      container_port   = each.value.port
    }
  }

  depends_on = [aws_lb_listener.http]

  tags = {
    Name    = "reactive-order-${each.key}"
    Service = each.key
  }
}


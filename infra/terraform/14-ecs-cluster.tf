# ===== ECS Cluster =====
# All 6 services run in this single cluster
resource "aws_ecs_cluster" "main" {
  name = "reactive-order-cluster"

  setting {
    name  = "containerInsights"
    value = "disabled" # Enable in prod for detailed metrics, costs extra
  }

  tags = {
    Name = "reactive-order-cluster"
  }
}


# ===== Service Connect Namespace =====
# Internal DNS so services call each other by name (replaces Docker Compose DNS)
# e.g. http://auth-service:8081, http://order-service:8082
resource "aws_service_discovery_http_namespace" "main" {
  name = "reactive-order"

  tags = {
    Name = "reactive-order-namespace"
  }
}


# ===== CloudWatch Log Groups =====
# One log group per service — ECS sends container stdout/stderr here
resource "aws_cloudwatch_log_group" "service" {
  for_each = toset(["auth", "gateway", "order", "inventory", "payment", "orchestrator"])
  name = "/ecs/reactive-order/${each.key}"
  retention_in_days = 7 # Keep logs 7 days only — minimal cost for demo

  tags = {
    Name = "reactive-order-${each.key}-logs"
    Service = each.key
  }
}
# ===== CloudWatch Alarms =====
# Basic alarms to know if something is wrong

# ALB — alert if too many 5xx errors (something is crashing)
resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "reactive-order-alb-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_ELB_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 10
  alarm_description   = "ALB returning too many 5xx errors"

  dimensions = {
    LoadBalancer = aws_alb.main.arn_suffix
  }

  tags = {
    Name = "reactive-order-alb-5xx"
  }
}

# ECS — alert if any service has no running tasks (service is down)
resource "aws_cloudwatch_metric_alarm" "ecs_running_tasks" {
  for_each = local.services

  alarm_name          = "reactive-order-${each.key}-no-running-tasks"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 2
  metric_name         = "RunningTaskCount"
  namespace           = "ECS/ContainerInsights"
  period              = 60
  statistic           = "Average"
  threshold           = 1
  alarm_description   = "${each.key} has no running tasks"

  dimensions = {
    ClusterName = aws_ecs_cluster.main.name
    ServiceName = each.key
  }

  tags = {
    Name    = "reactive-order-${each.key}-alarm"
    Service = each.key
  }
}

# RDS — alert if CPU is too high on any database
resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  for_each = local.databases

  alarm_name          = "reactive-order-${each.key}-db-high-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "${each.key} database CPU above 80%"

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.services[each.key].identifier
  }

  tags = {
    Name    = "reactive-order-${each.key}-db-cpu"
    Service = each.key
  }
}

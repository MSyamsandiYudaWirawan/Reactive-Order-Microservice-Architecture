# ===== ECS Task Execution Role =====
# Used by ECS itself to pull images, fetch secrets, write logs (infrastructure-level)

resource "aws_iam_role" "ecs_execution" {
  name = "reactive-order-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution_base" {
  role = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_policy" "secrets_access" {
  name = "reactive-order-secrets-access"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
        ]
        Resource = [
          aws_secretsmanager_secret.jwt_private_key.arn,
          aws_secretsmanager_secret.jwt_public_key.arn,
          "arn:aws:secretsmanager:ap-southeast-3:*:secret:reactive-order/*"
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "secrets_access" {
  policy_arn = aws_iam_policy.secrets_access.arn
  role       = aws_iam_role.ecs_execution.name
}

# ===== ECS Task Role =====
# Used by application code at runtime (app-level)
# Currently empty

resource "aws_iam_role" "ecs_task" {
  name = "reactive-order-ecs-task-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}
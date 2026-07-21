// aws_secretsmanager_secret is just a container for secrets
// aws_secretsmanager_secret_version is the actual secret value

# ===== JWT Keys =====
resource "aws_secretsmanager_secret" "jwt_private_key" {
  name                    = "reactive-order/jwt-private-key"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "jwt_private_key" {
  secret_id     = aws_secretsmanager_secret.jwt_private_key.id
  secret_string = file("${path.module}/module/keys/private_key.pem")
}

resource "aws_secretsmanager_secret" "jwt_public_key" {
  name                    = "reactive-order/jwt-public-key"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "jwt_public_key" {
  secret_id     = aws_secretsmanager_secret.jwt_public_key.id
  secret_string = file("${path.module}/module/keys/public_key.pem")
}

# ===== DB Credentials =====
//generate random password
resource "random_password" "db" {
  for_each = toset(["auth", "order", "inventory", "payment", "orchestrator"])
  length   = 24
  special  = false
}

//create secret container for each db's credentials
resource "aws_secretsmanager_secret" "db_credentials" {
  for_each = toset(["auth", "order", "inventory", "payment", "orchestrator"])
  name = "reactive-order/${each.key}-db"
  recovery_window_in_days = 0
}

//create secret value for each db's credentials
resource "aws_secretsmanager_secret_version" "db_credentials" {
  for_each = aws_secretsmanager_secret.db_credentials
  secret_id = each.value.id
  secret_string = jsonencode({
    username = "${each.key}_admin"
    password = random_password.db[each.key].result
    dbname   = "${each.key}_db"
  })
}

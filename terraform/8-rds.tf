locals {
  databases = {
    auth = { name = "auth_db", port = 5432 }
    order = { name = "order_db", port = 5432 }
    inventory = { name = "inventory_db", port = 5432}
    payment = { name = "payment_db", port = 5432}
    orchestrator = { name = "orchestrator_db", port = 5432}
  }
}


resource "aws_db_parameter_group" "no_ssl" {
  name   = "reactive-order-no-ssl"
  family = "postgres17"

  parameter {
    name  = "rds.force_ssl"
    value = "0"
  }
}

resource "aws_db_subnet_group" "main" {
  name = "reactive-order-db-subnet-group"
  subnet_ids = [aws_subnet.private-ap-southeast-3a.id, aws_subnet.private-ap-southeast-3b.id]
}

resource "aws_db_instance" "services" {
  for_each = local.databases
  identifier = "reactive-order-${each.key}"
  engine = "postgres"
  engine_version = "17.5"
  instance_class = "db.t3.micro"

  db_name = each.value.name
  username = jsondecode(aws_secretsmanager_secret_version.db_credentials[each.key].secret_string)["username"]
  password = jsondecode(aws_secretsmanager_secret_version.db_credentials[each.key].secret_string)["password"]
  port = each.value.port

  allocated_storage = 20
  storage_type = "gp3"

  db_subnet_group_name = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]  # attach the security group
  availability_zone = "ap-southeast-3a" # All in one AZ for demo

  publicly_accessible = false
  skip_final_snapshot = true

  # parameter_group_name = aws_db_parameter_group.no_ssl.name

  tags = {
    Name = "reactive-order-${each.key}-db"
    Service = each.key
  }
}

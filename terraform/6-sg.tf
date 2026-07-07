// ALB - only accept HTTP from internet
resource "aws_security_group" "alb" {
  name = "reactive-order-alb-sg"
  vpc_id = aws_vpc.vpc.id

  ingress {
    from_port = 80
    to_port = 80
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

// allow traffic from ALB to gateway only
resource "aws_security_group" "gateway" {
  name = "gateway-sg"
  vpc_id = aws_vpc.vpc.id

  ingress {
    from_port = 8080
    to_port = 8080
    protocol = "tcp"
    security_groups = [aws_security_group.alb.id]
  }
  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

}

// port range  8080 - 8085 is needed for direct call service to service including gateway
resource "aws_security_group" "ecs" {
  name = "ecs-sg"
  vpc_id = aws_vpc.vpc.id

  ingress {
    from_port = 8080
    to_port = 8085
    protocol = "tcp"
  }

  egress {
    from_port = 8080
    to_port = 8085
    protocol = "tcp"
    self = true # ECS tasks can talk to each other (service-to-service)
  }

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "db" {
  name = "db-sg"
  vpc_id = aws_vpc.vpc.id

  ingress {
    from_port = 5432
    to_port = 5432
    protocol = "tcp"
    security_groups = [aws_security_group.ecs.id] # Only ECS can reach DB
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "messaging" {
  name = "messaging-sg"
  vpc_id = aws_vpc.vpc.id

  ingress {
    from_port = 6379
    to_port = 6379
    protocol = "tcp"
    security_groups = [aws_security_group.ecs.id] #Redis
  }

  ingress {
    from_port = 9092
    to_port = 9092
    protocol = "tcp"
    security_groups = [aws_security_group.ecs.id] #Kafka
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
# ===== Application Load Balancer =====
# Public-facing entry point — receives internet traffic and forwards to gateway-service
resource "aws_alb" "main" {
  name = "reactive-order-alb"
  internal = false  #public-facing ALB
  load_balancer_type = "application"
  security_groups = [aws_security_group.alb.id]
  subnets = [aws_subnet.public-ap-southeast-3a.id, aws_subnet.public-ap-southeast-3b.id]

  tags = {
    Name = "reactive-order-alb"
  }
}

# ===== Target Group =====
# ALB forwards traffic here — only gateway-service registered as target
# target_type = "ip" because Fargate uses IP-based networking (no EC2 instances)
resource "aws_lb_target_group" "gateway" {
  name = "reactive-order-gateway-tg"
  port = 8080
  protocol = "HTTP"
  vpc_id = aws_vpc.vpc.id
  target_type = "ip" #ECS is ip-based, not instance-based

  health_check {
    path = "/actuator/health"
    protocol = "HTTP"
    port = 8080
    interval = 30
    healthy_threshold = 2
    unhealthy_threshold = 3
  }

  tags = {
    Name = "reactive-order-gateway-tg"
  }
}

# ===== Listener =====
# ALB listens on port 80 and forwards all traffic to gateway target group
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_alb.main.arn
  port = 80 #80(http) for demo prod -> 443(https) with acm certificate
  protocol = "HTTP"

  default_action {
    type = "forward"
    target_group_arn = aws_lb_target_group.gateway.arn
  }
}
resource "aws_ecr_repository" "services" {
  for_each = toset(["auth","gateway","order","inventory","payment","orchestrator"])

  name = "reactive-order/${each.key}"
  force_delete = true

  //free vulnerability scan
  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "reactive-order-${each.key}"
    Service = each.key
  }
}
output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer"
  value       = aws_alb.main.dns_name
}

output "cluster_name" {
  description = "Name of the ECS cluster"
  value       = aws_ecs_cluster.main.name
}

output "vpc_id" {
  description = "ID of the VPC"
  value       = aws_vpc.vpc.id
}
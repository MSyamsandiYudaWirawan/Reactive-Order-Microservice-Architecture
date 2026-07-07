resource "aws_elasticache_subnet_group" "main" {
  name = "reactive-order-redis-subnet-group"
  subnet_ids = [aws_subnet.private-ap-southeast-3a.id, aws_subnet.private-ap-southeast-3b.id]
}

resource "aws_elasticache_cluster" "redis" {
  cluster_id = "reactive-order-redis"
  engine = "redis"
  engine_version = "7.1"
  node_type = "cache.t3.micro"
  num_cache_nodes = 1
  port = 6379
  subnet_group_name = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.messaging.id]
  az_mode = "single-az"
  preferred_availability_zones = "ap-southeast-3a" # Elastic in one AZ only for demo

  tags = {
    Name = "reactive-order-redis"
  }
}
resource "aws_msk_cluster" "main" {
  cluster_name           = "reactive-order-kafka"
  kafka_version          = "3.7.x.kraft"
  number_of_broker_nodes = 1

  broker_node_group_info {
    instance_type   = "kafka.t3.small"
    client_subnets  = [aws_subnet.private-ap-southeast-3a.id]
    security_groups = [aws_security_group.messaging.id]

    storage_info {
      ebs_storage_info {
        volume_size = 100
      }
    }
  }
  // no encrypt
  encryption_info {
    encryption_in_transit {
      client_broker = "PLAINTEXT"
      in_cluster    = false
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk.name
      }
    }
  }

  tags = {
    Name = "reactive-order-kafka"
  }
}

resource "aws_cloudwatch_log_group" "msk" {
  name              = "/aws/msk/reactive-order"
  retention_in_days = 7
}

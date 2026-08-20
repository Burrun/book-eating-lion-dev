resource "aws_elasticache_subnet_group" "this" {
  name       = "lion-team3-${var.environment}-valkey"
  subnet_ids = var.data_subnet_ids
}

resource "aws_security_group" "valkey" {
  name_prefix = "lion-team3-${var.environment}-valkey-"
  description = "Valkey SG - allows 6379 from app tier only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Valkey from EKS app tier"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [var.app_security_group_id]
  }

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "lion-team3-${var.environment}-valkey-sg"
  }
}

# noeviction: 캐시가 아니라 Redisson 락/Streams/Pub-Sub 상태도 담기 때문에
# 메모리가 차도 키를 조용히 지우면 안 된다 - 지우는 대신 OOM 에러로 드러나야 한다.
resource "aws_elasticache_parameter_group" "this" {
  name   = "lion-team3-${var.environment}-valkey8"
  family = "valkey8"

  parameter {
    name  = "maxmemory-policy"
    value = "noeviction"
  }
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = "lion-team3-${var.environment}"
  description          = "lion-team3 ${var.environment} Valkey"

  engine         = "valkey"
  engine_version = "8.2"
  node_type      = var.node_type

  subnet_group_name    = aws_elasticache_subnet_group.this.name
  security_group_ids   = [aws_security_group.valkey.id]
  parameter_group_name = aws_elasticache_parameter_group.this.name

  # cluster_mode_enabled라는 인자는 없다 - num_cache_clusters를 쓰면 그 자체로
  # Cluster Mode Disabled(전통적 Primary/Replica) 구성이 된다. num_node_groups를
  # 쓰면 Cluster Mode Enabled(샤딩)가 되므로 그쪽과는 상호 배타적이다.
  num_cache_clusters = 1 + var.replica_count

  # Failover/Multi-AZ는 Replica가 최소 1개 있어야 켤 수 있다(AWS 제약).
  # replica_count = 0(dev 비용 절감용)이면 Primary 단일 노드라 자동으로 꺼진다.
  automatic_failover_enabled = var.replica_count > 0
  multi_az_enabled           = var.replica_count > 0

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
}

resource "aws_cloudwatch_metric_alarm" "memory" {
  alarm_name          = "lion-team3-${var.environment}-valkey-memory"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "DatabaseMemoryUsagePercentage"
  namespace           = "AWS/ElastiCache"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Valkey memory usage high"
  alarm_actions       = [var.sns_topic_arn]

  dimensions = {
    ReplicationGroupId = aws_elasticache_replication_group.this.replication_group_id
  }
}

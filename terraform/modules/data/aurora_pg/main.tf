resource "aws_db_subnet_group" "this" {
  name       = "book-eating-lion-${var.environment}-aurora"
  subnet_ids = var.data_subnet_ids
}

resource "aws_security_group" "cluster" {
  name_prefix = "book-eating-lion-${var.environment}-aurora-"
  description = "Aurora cluster SG - allows 5432 from app tier only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "PostgreSQL from EKS app tier"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.app_security_group_id]
  }

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "book-eating-lion-${var.environment}-aurora-sg"
  }
}

resource "aws_rds_cluster" "this" {
  cluster_identifier     = "book-eating-lion-${var.environment}"
  engine                 = "aurora-postgresql"
  engine_mode            = "provisioned" # Serverless v2는 provisioned 클러스터 위에서 인스턴스별로 켠다
  engine_version         = "16.4"
  database_name          = var.database_name
  master_username        = var.master_username
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.cluster.id]

  # 마스터 비밀번호를 tfvars/state에 평문으로 두지 않기 위해 AWS가 Secrets Manager에
  # 자동 발급하게 한다 (TERRAFORM_STRUCTURE.md §3.2-1 참고).
  manage_master_user_password = true

  serverlessv2_scaling_configuration {
    min_capacity = var.min_capacity
    max_capacity = var.max_capacity
  }

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "book-eating-lion-${var.environment}-final-${formatdate("YYYYMMDDhhmmss", timestamp())}"

  # deletion_protection(AWS API 레벨, 콘솔/CLI에서도 막음)이 실제 안전장치다.
  # Terraform lifecycle.prevent_destroy는 변수를 받을 수 없어(리터럴만 허용) 환경별로
  # 조건부 적용이 불가능하므로 여기서는 쓰지 않는다 - deletion_protection이 더 강한 보호다.

  lifecycle {
    ignore_changes = [final_snapshot_identifier]
  }
}

# Writer 1 + Reader(reader_count)개. Aurora는 인스턴스에 역할을 직접 지정하지 않는다 -
# 첫 번째로 만들어진 인스턴스가 초기 Writer가 되고, 이후 Failover로 바뀔 수 있다.
resource "aws_rds_cluster_instance" "this" {
  count = 1 + var.reader_count

  identifier           = "book-eating-lion-${var.environment}-${count.index}"
  cluster_identifier   = aws_rds_cluster.this.id
  instance_class       = "db.serverless"
  engine               = aws_rds_cluster.this.engine
  engine_version       = aws_rds_cluster.this.engine_version
  db_subnet_group_name = aws_db_subnet_group.this.name
}

resource "aws_cloudwatch_metric_alarm" "connections" {
  alarm_name          = "book-eating-lion-${var.environment}-aurora-connections"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = 200
  alarm_description   = "Aurora DB connection count high - possible connection exhaustion under HPA scale-out"
  alarm_actions       = [var.sns_topic_arn]

  dimensions = {
    DBClusterIdentifier = aws_rds_cluster.this.cluster_identifier
  }
}

resource "aws_cloudwatch_metric_alarm" "cpu" {
  alarm_name          = "book-eating-lion-${var.environment}-aurora-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Aurora CPU utilization high"
  alarm_actions       = [var.sns_topic_arn]

  dimensions = {
    DBClusterIdentifier = aws_rds_cluster.this.cluster_identifier
  }
}

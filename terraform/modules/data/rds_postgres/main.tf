# 단일 인스턴스 RDS for PostgreSQL.
#
# aurora_pg와 나란히 두는 이유: integrated prod는 "EC2 자체 설치 Postgres -> 관리형 RDS"
# 전환의 비교 대상이라 Aurora가 아니라 EC2와 같은 급의 단일 인스턴스여야 한다
# (t4g.micro <-> db.t4g.micro, gp3 30GB). Aurora로 가면 스토리지 아키텍처부터 달라져
# 그 비교가 성립하지 않는다.
#
# 출력 이름은 aurora_pg와 맞춰 뒀다(security_group_id/master_user_secret_arn 등) -
# 01-data가 두 모듈 중 어느 쪽을 부르든 아래 배선(rds_proxy, database_private_dns,
# SSM 파라미터)을 그대로 쓸 수 있게 하기 위해서다.

resource "aws_db_subnet_group" "this" {
  name       = "lion-team3-${var.environment}-rds"
  subnet_ids = var.data_subnet_ids
}

resource "aws_security_group" "this" {
  name_prefix = "lion-team3-${var.environment}-rds-"
  description = "RDS PostgreSQL SG - allows 5432 from app tier only"
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
    Name = "lion-team3-${var.environment}-rds-sg"
  }
}

resource "aws_db_instance" "this" {
  identifier     = "lion-team3-${var.environment}"
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  db_name  = var.database_name
  username = var.master_username

  # 마스터 비밀번호를 tfvars/state에 평문으로 두지 않기 위해 AWS가 Secrets Manager에
  # 자동 발급하게 한다 (aurora_pg와 동일 - TERRAFORM_STRUCTURE.md §3.2-1 참고).
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.this.id]
  publicly_accessible    = false

  # EC2 쪽 루트 볼륨(gp3 30GiB)과 같은 급으로 맞춘다. gp3는 이 용량대에서
  # 3000 IOPS / 125 MBps가 baseline이라 EC2와 동일 조건이 된다.
  storage_type          = "gp3"
  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage

  # 기본값이 false라 명시하지 않으면 저장 데이터가 암호화되지 않는다.
  storage_encrypted = true

  # 비교 대상인 EC2가 단일 인스턴스라 Multi-AZ를 켜면 조건이 어긋난다.
  # 실제 운영으로 승격할 때 var로 true를 넘긴다.
  multi_az = var.multi_az

  backup_retention_period = var.backup_retention_period
  deletion_protection     = var.deletion_protection
  skip_final_snapshot     = var.skip_final_snapshot
  final_snapshot_identifier = (
    var.skip_final_snapshot ? null : "lion-team3-${var.environment}-final-${formatdate("YYYYMMDDhhmmss", timestamp())}"
  )

  auto_minor_version_upgrade = true
  apply_immediately          = var.apply_immediately

  # deletion_protection(AWS API 레벨)이 실제 안전장치다. lifecycle.prevent_destroy는
  # 변수를 받을 수 없어 환경별 조건부 적용이 불가능하다(aurora_pg와 같은 판단).
  lifecycle {
    ignore_changes = [final_snapshot_identifier]
  }
}

# ── 리드 리플리카 (기본 비활성) ─────────────────────────────────────
# 원 설계는 read/write 노드 분리이고 k8s도 db-primary-service / db-reader-service
# 두 개로 갈라져 있다. 그런데 앱 쪽 라우팅이 아직 없어서 지금 켜면 깨진다 -
# 이유는 variables.tf의 read_replica_count 주석 참고(catalog가 서비스 통째로
# reader를 봐서 쓰기와 Liquibase가 read-only 트랜잭션에서 죽는다).
#
# RDS Proxy(비-Aurora)는 타깃이 인스턴스 하나뿐이라 읽기/쓰기 분기를 못 한다.
# 그래서 라우팅이 들어간 뒤에도 쓰기만 Proxy를 거치고(커넥션 풀링이 필요한 쪽),
# 읽기는 리플리카에 직접 붙는 구조가 된다.
#
# 소스 인스턴스에 backup_retention_period > 0 이어야 리플리카를 만들 수 있다.
resource "aws_db_instance" "replica" {
  count = var.read_replica_count

  identifier          = "lion-team3-${var.environment}-reader-${count.index}"
  replicate_source_db = aws_db_instance.this.identifier

  # 리플리카는 소스에서 상속받는 값이 많다(engine/db_name/username/storage_encrypted).
  # 인스턴스 클래스는 상속되지 않으므로 명시한다 - 기본은 소스와 동급.
  instance_class = coalesce(var.replica_instance_class, var.instance_class)

  vpc_security_group_ids = [aws_security_group.this.id]
  publicly_accessible    = false

  # 리플리카는 소스에서 다시 만들면 되므로 최종 스냅샷이 무의미하다.
  skip_final_snapshot = true

  # 리플리카 자체 백업은 끈다(소스가 이미 PITR을 갖는다).
  backup_retention_period = 0

  auto_minor_version_upgrade = true
  apply_immediately          = var.apply_immediately

  tags = {
    Name = "lion-team3-${var.environment}-rds-reader-${count.index}"
    Role = "reader"
  }
}

resource "aws_cloudwatch_metric_alarm" "connections" {
  alarm_name          = "lion-team3-${var.environment}-rds-connections"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = var.connection_alarm_threshold
  alarm_description   = "RDS DB connection count high - possible connection exhaustion under HPA scale-out"
  alarm_actions       = [var.sns_topic_arn]

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.this.identifier
  }
}

resource "aws_cloudwatch_metric_alarm" "cpu" {
  alarm_name          = "lion-team3-${var.environment}-rds-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "RDS CPU utilization high"
  alarm_actions       = [var.sns_topic_arn]

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.this.identifier
  }
}

# db.t4g.micro는 메모리가 1GiB라 FreeableMemory가 먼저 바닥난다 - EC2 t4g.micro에서
# 겪던 것과 같은 실패 모드라 비교 관측 지점으로 하나 더 둔다(aurora_pg엔 없는 알람).
resource "aws_cloudwatch_metric_alarm" "freeable_memory" {
  alarm_name          = "lion-team3-${var.environment}-rds-freeable-memory"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 3
  metric_name         = "FreeableMemory"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = var.freeable_memory_alarm_bytes
  alarm_description   = "RDS freeable memory low - undersized instance class for current load"
  alarm_actions       = [var.sns_topic_arn]

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.this.identifier
  }
}

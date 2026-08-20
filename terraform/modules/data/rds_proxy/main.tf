# Proxy 전용 보안그룹. app 계층에서 Proxy로(5432), Proxy에서 Aurora로(5432) 두 홉을 연다.
resource "aws_security_group" "proxy" {
  name_prefix = "lion-team3-${var.environment}-rds-proxy-"
  description = "RDS Proxy SG - app tier in, Aurora cluster out"
  vpc_id      = var.vpc_id

  ingress {
    description     = "PostgreSQL from EKS app tier"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.app_security_group_id]
  }

  egress {
    description     = "PostgreSQL to Aurora cluster"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.cluster_security_group_id]
  }

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "lion-team3-${var.environment}-rds-proxy-sg"
  }
}

# aurora_pg의 클러스터 SG는 app_security_group_id에서만 인바운드를 열어뒀다.
# Proxy를 거치면 트래픽 출발지가 Proxy SG로 바뀌므로, 클러스터 SG에 이 규칙을 추가해야 한다.
resource "aws_security_group_rule" "cluster_allow_proxy" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  security_group_id        = var.cluster_security_group_id
  source_security_group_id = aws_security_group.proxy.id
  description              = "PostgreSQL from RDS Proxy"
}

resource "aws_iam_role" "proxy" {
  name = "lion-team3-${var.environment}-rds-proxy"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "rds.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "proxy_secrets" {
  name = "read-db-secret"
  role = aws_iam_role.proxy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = [var.secrets_manager_arn]
    }]
  })
}

resource "aws_db_proxy" "this" {
  name                   = "lion-team3-${var.environment}"
  engine_family          = "POSTGRESQL"
  role_arn               = aws_iam_role.proxy.arn
  vpc_subnet_ids         = var.data_subnet_ids
  vpc_security_group_ids = [aws_security_group.proxy.id]
  require_tls            = true

  auth {
    auth_scheme = "SECRETS"
    iam_auth    = "DISABLED"
    secret_arn  = var.secrets_manager_arn
  }
}

resource "aws_db_proxy_default_target_group" "this" {
  db_proxy_name = aws_db_proxy.this.name

  connection_pool_config {
    max_connections_percent = 100
  }
}

resource "aws_db_proxy_target" "this" {
  db_proxy_name         = aws_db_proxy.this.name
  target_group_name     = aws_db_proxy_default_target_group.this.name
  db_cluster_identifier = var.aurora_cluster_identifier
}

# Dev 환경 전용 비용 절감형 PostgreSQL. prod에는 절대 쓰지 않는다
# (Multi-AZ/자동 백업 없음 - TERRAFORM_STRUCTURE.md §6.3 참고).
#
# 출력값 이름을 aurora_pg와 최대한 맞춰서, 02-runtime이 dev/prod 어느 쪽이든
# 같은 SSM 파라미터 이름으로 DB 정보를 읽을 수 있게 한다.

data "aws_region" "current" {}

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-arm64"]
  }

  filter {
    name   = "architecture"
    values = ["arm64"]
  }
}

resource "random_password" "master" {
  length  = 24
  special = false # 접속 문자열/유저데이터 스크립트에 그대로 쓰이므로 특수문자로 인한 이스케이프 문제 방지
}

resource "aws_secretsmanager_secret" "master" {
  name = "book-eating-lion-${var.environment}-ec2-postgres-master"
}

resource "aws_secretsmanager_secret_version" "master" {
  secret_id = aws_secretsmanager_secret.master.id
  secret_string = jsonencode({
    username = var.master_username
    password = random_password.master.result
  })
}

resource "aws_security_group" "this" {
  name_prefix = "book-eating-lion-${var.environment}-ec2-postgres-"
  description = "Dev PostgreSQL SG - allows 5432 from app tier only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "PostgreSQL from EKS app tier"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.app_security_group_id]
  }

  egress {
    description = "Package install / SSM"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "book-eating-lion-${var.environment}-ec2-postgres-sg"
  }
}

# SSH 키/포트 없이 관리 - Session Manager로만 접속한다.
resource "aws_iam_role" "ssm" {
  name = "book-eating-lion-${var.environment}-ec2-postgres-ssm"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.ssm.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# user_data에 비밀번호를 직접 심으면 Terraform state와 EC2 user-data 메타데이터에
# 평문으로 남는다 - Secrets Manager를 따로 만든 목적 자체가 무너진다. 그래서 인스턴스가
# 부팅 시점에 이 권한으로 Secrets Manager에서 직접 읽어오게 한다.
resource "aws_iam_role_policy" "read_secret" {
  name = "read-master-secret"
  role = aws_iam_role.ssm.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = [aws_secretsmanager_secret.master.arn]
    }]
  })
}

resource "aws_iam_instance_profile" "this" {
  name = "book-eating-lion-${var.environment}-ec2-postgres"
  role = aws_iam_role.ssm.name
}

resource "aws_instance" "this" {
  ami                    = data.aws_ami.amazon_linux.id
  instance_type          = var.instance_type
  subnet_id              = var.app_subnet_id
  vpc_security_group_ids = [aws_security_group.this.id]
  iam_instance_profile   = aws_iam_instance_profile.this.name

  root_block_device {
    volume_size = var.root_volume_size_gb
    volume_type = "gp3"
    encrypted   = true
  }

  # PostgreSQL 16 설치 + 마스터 계정 생성. 스키마(00-init.sql, 01~04-*.sql) 적용은
  # 이 모듈의 책임이 아니다 - db/postgres/*.sql을 psql로 실행하는 건 배포 파이프라인/
  # 애플리케이션 쪽 몫이다 (docker-compose가 로컬에서 하는 것과 같은 역할).
  #
  # 비밀번호는 스크립트에 직접 넣지 않는다 - 부팅 시점에 Secrets Manager에서 읽어온다
  # (var.aws_region이 없어 aws cli의 기본 리전 탐지에 의존하지 않도록 명시적으로 넘김).
  user_data = <<-EOF
    #!/bin/bash
    set -euxo pipefail
    dnf install -y postgresql16-server postgresql16
    postgresql-setup --initdb
    systemctl enable --now postgresql

    DB_PASSWORD=$(aws secretsmanager get-secret-value \
      --secret-id ${aws_secretsmanager_secret.master.arn} \
      --region ${data.aws_region.current.name} \
      --query SecretString --output text \
      | python3 -c "import json,sys; print(json.load(sys.stdin)['password'])")

    sudo -u postgres psql -c "ALTER USER postgres PASSWORD '$DB_PASSWORD';"
    sudo -u postgres psql -c "CREATE ROLE ${var.master_username} LOGIN SUPERUSER PASSWORD '$DB_PASSWORD';"
    sudo -u postgres createdb -O ${var.master_username} ${var.database_name}

    sed -i "s/#listen_addresses = 'localhost'/listen_addresses = '*'/" /var/lib/pgsql/data/postgresql.conf
    echo "host all all 0.0.0.0/0 scram-sha-256" >> /var/lib/pgsql/data/pg_hba.conf
    systemctl restart postgresql
  EOF

  tags = {
    Name = "book-eating-lion-${var.environment}-ec2-postgres"
  }
}

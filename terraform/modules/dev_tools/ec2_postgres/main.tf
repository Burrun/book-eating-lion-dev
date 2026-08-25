# Dev 환경 전용 비용 절감형 PostgreSQL. prod에는 절대 쓰지 않는다
# (Multi-AZ/자동 백업 없음 - TERRAFORM_STRUCTURE.md §6.3 참고).
#
# 출력값 이름을 aurora_pg와 최대한 맞춰서, 02-runtime이 dev/prod 어느 쪽이든
# 같은 SSM 파라미터 이름으로 DB 정보를 읽을 수 있게 한다.

data "aws_region" "current" {}

# pg_hba.conf를 SG(app_security_group_id)뿐 아니라 DB 인증 레벨에서도 VPC 대역으로
# 제한하기 위해 조회 - SG가 나중에 느슨해져도(예: 실수로 0.0.0.0/0 추가) DB 인증
# 자체는 VPC 밖에서 들어오는 접속을 거부한다.
data "aws_vpc" "this" {
  id = var.vpc_id
}

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
  name = "lion-team3-${var.environment}-ec2-postgres-master"
}

resource "aws_secretsmanager_secret_version" "master" {
  secret_id = aws_secretsmanager_secret.master.id
  secret_string = jsonencode({
    username = var.master_username
    password = random_password.master.result
  })
}

resource "aws_security_group" "this" {
  name_prefix = "lion-team3-${var.environment}-ec2-postgres-"
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
    Name = "lion-team3-${var.environment}-ec2-postgres-sg"
  }
}

# SSH 키/포트 없이 관리 - Session Manager로만 접속한다.
resource "aws_iam_role" "ssm" {
  name = "lion-team3-${var.environment}-ec2-postgres-ssm"

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
  name = "lion-team3-${var.environment}-ec2-postgres"
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

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required" # IMDSv1 비활성화
  }

  # 개발 DB는 상태를 가진 장기 실행 인스턴스다. data.aws_ami의 most_recent 결과가
  # 바뀌었다는 이유만으로 인스턴스를 교체하면 DB 데이터가 유실될 수 있으므로,
  # AMI 갱신은 별도의 백업/마이그레이션 작업으로만 수행한다.
  lifecycle {
    # user_data는 최초 생성 시에만 사용한다. 이후 DB 이름 변경 때문에 상태를 가진
    # 인스턴스가 교체되거나 cloud-init 재실행에 의존하지 않도록 아래 SSM Association이
    # 추가 데이터베이스를 멱등하게 생성한다.
    ignore_changes = [ami, user_data]
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
    # AL2023 dnf 레포에는 "awscli"라는 패키지 자체가 없다(AWS CLI v2는 AL2023
    # 기본 AMI에 이미 preinstall돼 있고, 리포로 배포되지 않는다) - dnf install에
    # 넣으면 "No match for argument" 에러로 postgresql 설치 전에 부팅이 죽는다.
    # 그래서 설치를 시도하지 않고 존재 여부만 확인해 실패를 명확하게 만든다.
    dnf install -y postgresql16-server postgresql16 python3
    command -v aws >/dev/null 2>&1 || { echo "aws cli not found on this AMI" >&2; exit 1; }
    postgresql-setup --initdb
    systemctl enable --now postgresql

    DB_PASSWORD=$(aws secretsmanager get-secret-value \
      --secret-id ${aws_secretsmanager_secret.master.arn} \
      --region ${data.aws_region.current.name} \
      --query SecretString --output text \
      | python3 -c "import json,sys; print(json.load(sys.stdin)['password'])")

    # Secrets Manager 조회/파싱이 실패하면 빈 비밀번호로 계속 진행하지 않고
    # 여기서 바로 중단한다 - psql 자체는 성공 종료 코드를 반환할 수 있어
    # set -e만으로는 이 실패를 못 잡는다.
    if [ -z "$DB_PASSWORD" ]; then
      echo "Failed to retrieve DB password from Secrets Manager" >&2
      exit 1
    fi

    sudo -u postgres psql -c "ALTER USER postgres PASSWORD '$DB_PASSWORD';"
    sudo -u postgres psql -c "CREATE ROLE ${var.master_username} LOGIN SUPERUSER PASSWORD '$DB_PASSWORD';"
    sudo -u postgres createdb -O ${var.master_username} ${var.database_name}

    sed -i "s/#listen_addresses = 'localhost'/listen_addresses = '*'/" /var/lib/pgsql/data/postgresql.conf
    echo "host all all ${data.aws_vpc.this.cidr_block} scram-sha-256" >> /var/lib/pgsql/data/pg_hba.conf
    systemctl restart postgresql
  EOF

  tags = {
    Name = "lion-team3-${var.environment}-ec2-postgres"
  }
}

# 기존 EC2 PostgreSQL을 교체하지 않고 환경별 논리 DB를 보장한다.
# 이미 존재하면 아무 작업도 하지 않으므로 반복 apply해도 안전하다.
resource "aws_ssm_association" "ensure_database" {
  name             = "AWS-RunShellScript"
  association_name = "lion-team3-${var.environment}-ensure-${var.database_name}"

  targets {
    key    = "InstanceIds"
    values = [aws_instance.this.id]
  }

  parameters = {
    commands = join("\n", [
      "set -eu",
      "until systemctl is-active --quiet postgresql; do sleep 5; done",
      "if ! sudo -u postgres psql -tAc \"SELECT 1 FROM pg_database WHERE datname = '${var.database_name}'\" | grep -q 1; then",
      "  sudo -u postgres createdb -O ${var.master_username} ${var.database_name}",
      "fi",
      "sudo -u postgres psql --dbname=${var.database_name} --set=ON_ERROR_STOP=1 --command=\"CREATE SCHEMA IF NOT EXISTS member_db AUTHORIZATION ${var.master_username}; CREATE SCHEMA IF NOT EXISTS catalog_db AUTHORIZATION ${var.master_username}; CREATE SCHEMA IF NOT EXISTS order_db AUTHORIZATION ${var.master_username}; CREATE SCHEMA IF NOT EXISTS ai_db AUTHORIZATION ${var.master_username};\"",
    ])
  }

  depends_on = [aws_instance.this]
}

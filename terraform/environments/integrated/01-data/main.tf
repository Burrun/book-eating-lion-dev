# integrated 01-data
#
# prod DB는 dev의 EC2 Postgres에 얹혀 있던 것을 전용 RDS로 분리했다(2026-09-02).
# 이전 구성은 dev/01-data가 만든 EC2 인스턴스를 data source로 참조해 그 안에
# bookdb_prod를 추가하는 방식이었다 - dev 장애가 prod로 그대로 전이되고,
# 관리형 DB와의 비교 대상도 없었다.
#
# Aurora가 아니라 단일 인스턴스 RDS인 이유: 이 전환은 "EC2 자체 설치 Postgres ->
# 관리형 RDS"의 비교 실험이라 EC2와 같은 급이어야 한다(t4g.micro <-> db.t4g.micro,
# gp3 30GiB). Aurora로 가면 스토리지 아키텍처부터 달라져 비교가 성립하지 않는다.
#
# 동시에 서비스 계정 분리도 여기서 처음으로 실제 적용된다. 그 전까지는
# sync-github-config.sh가 마스터 계정(bookadmin) 하나를 GitHub Secrets 8개에
# 복제해서, README가 주장하는 스키마별 권한 경계가 배포 환경엔 없었다.
#
# 이 계층이 하지 않는 것: DB 안에 롤/스키마를 만드는 일. RDS가 프라이빗 서브넷에
# 있어 CI 러너가 접속할 수 없다. db/postgres/00-init.sql을 VPC 안(dev EC2 또는
# k8s Job)에서 별도로 실행해야 한다 - 순서는 docs/RDS-MIGRATION.md 참고.

locals {
  ssm_prefix                = "/${var.environment}"
  database_private_dns_zone = "db.${var.environment}.internal.ajttk.com"

  ai_channel_ssm_values = {
    "ai/ingest_channel_arn"   = module.ai_pipeline.ingest_channel_arn
    "ai/ingest_channel_url"   = module.ai_pipeline.ingest_channel_url
    "ai/purchase_channel_arn" = module.ai_pipeline.purchase_channel_arn
    "ai/purchase_channel_url" = module.ai_pipeline.purchase_channel_url
  }
}

data "aws_ssm_parameter" "data_subnet_ids" {
  name = "${local.ssm_prefix}/network/data_subnet_ids"
}

data "aws_ssm_parameter" "vpc_id" {
  name = "${local.ssm_prefix}/network/vpc_id"
}

data "aws_ssm_parameter" "app_security_group_id" {
  name = "${local.ssm_prefix}/network/app_security_group_id"
}

data "aws_ssm_parameter" "sns_topic_arn" {
  name = "${local.ssm_prefix}/alerting/sns_topic_arn"
}

# ── prod 전용 DB (EC2 얹혀살기 청산) ────────────────────────────────
module "rds_postgres" {
  source = "../../../modules/data/rds_postgres"

  environment           = var.environment
  vpc_id                = data.aws_ssm_parameter.vpc_id.value
  data_subnet_ids       = split(",", data.aws_ssm_parameter.data_subnet_ids.value)
  app_security_group_id = data.aws_ssm_parameter.app_security_group_id.value
  sns_topic_arn         = data.aws_ssm_parameter.sns_topic_arn.value

  database_name   = var.database_name
  master_username = var.master_username

  engine_version          = var.rds_engine_version
  instance_class          = var.rds_instance_class
  allocated_storage       = var.rds_allocated_storage
  multi_az                = var.rds_multi_az
  backup_retention_period = var.rds_backup_retention_period
  deletion_protection     = var.rds_deletion_protection
  skip_final_snapshot     = var.rds_skip_final_snapshot
  apply_immediately       = var.rds_apply_immediately
}

# 서비스별 DB 계정 비밀번호. DB 안의 롤 생성은 db/postgres/00-init.sql이 담당한다
# (CI 러너가 프라이빗 서브넷의 RDS에 못 붙어서 Terraform이 할 수 없다).
module "db_service_accounts" {
  source = "../../../modules/data/db_service_accounts"

  environment = var.environment
}

module "rds_proxy" {
  source = "../../../modules/data/rds_proxy"

  environment               = var.environment
  vpc_id                    = data.aws_ssm_parameter.vpc_id.value
  data_subnet_ids           = split(",", data.aws_ssm_parameter.data_subnet_ids.value)
  app_security_group_id     = data.aws_ssm_parameter.app_security_group_id.value
  cluster_security_group_id = module.rds_postgres.security_group_id
  db_instance_identifier    = module.rds_postgres.instance_identifier
  secrets_manager_arn       = module.rds_postgres.master_user_secret_arn

  # 이게 비어 있으면 앱이 쓰는 4개 계정이 전부 Proxy에서 인증 거부된다.
  additional_auth_secret_arns = module.db_service_accounts.secret_arns
}

module "database_private_dns" {
  source = "../../../modules/data/database_private_dns"

  environment = var.environment
  vpc_id      = data.aws_ssm_parameter.vpc_id.value
  zone_name   = local.database_private_dns_zone

  writer_target = module.rds_proxy.proxy_endpoint

  # 지금은 리드 리플리카가 없어(read_replica_count = 0) Writer와 같은 인스턴스를
  # 가리킨다. catalog가 이 이름으로 쓰기까지 하고 있어서, 라우팅이 들어가기 전에
  # 진짜 리플리카를 붙이면 catalog가 죽는다 - rds_postgres 모듈 주석 참고.
  # Reader는 Proxy를 거치지 않는다(쓰기만 커넥션 풀링 대상).
  reader_target = module.rds_postgres.reader_endpoint
}

# ── dev EC2를 이관 점프박스로 씀 (db/postgres/00-init.sql, pg_dump/restore) ──
# RDS가 프라이빗 서브넷에 있어 CI 러너/로컬에서 직접 못 붙는다. dev의 EC2
# Postgres(SSM 세션 진입 가능)를 점프박스로 쓰기로 docs/RDS-MIGRATION.md에서
# 정했는데, 그 인스턴스의 IAM 역할(modules/dev_tools/ec2_postgres)은 자기
# 자신의 마스터 시크릿만 읽을 수 있어서 여기서 만든 마스터/서비스 계정 시크릿을
# 못 읽는다(2026-09-02 00-init.sql 실행 중 AccessDeniedException으로 확인).
#
# 역할은 이름으로 참조한다(모듈 output이 아니라 data source) - dev/01-data와
# 이 계층은 서로 다른 state라 리소스를 직접 참조할 수 없다. 이름 규칙은
# modules/dev_tools/ec2_postgres/main.tf의 aws_iam_role.ssm과 동일해야 한다.
#
# 이관이 끝나면 이 권한은 더 필요 없다 - docs/TODOS.md에
data "aws_iam_role" "dev_ec2_postgres" {
  name = "lion-team3-dev-ec2-postgres-ssm"
}

resource "aws_iam_role_policy" "dev_ec2_postgres_read_integrated_secrets" {
  name = "read-integrated-db-secrets-for-migration"
  role = data.aws_iam_role.dev_ec2_postgres.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["secretsmanager:GetSecretValue"]
      Resource = concat(
        [module.rds_postgres.master_user_secret_arn],
        module.db_service_accounts.secret_arns,
      )
    }]
  })
}

# ── prod 전용 리소스 (dev와 공유 안 함, 새로 만듦) ──────────────────
module "cache_valkey" {
  source = "../../../modules/data/cache_valkey"

  environment           = var.environment
  vpc_id                = data.aws_ssm_parameter.vpc_id.value
  data_subnet_ids       = split(",", data.aws_ssm_parameter.data_subnet_ids.value)
  app_security_group_id = data.aws_ssm_parameter.app_security_group_id.value
  node_type             = var.valkey_node_type
  sns_topic_arn         = data.aws_ssm_parameter.sns_topic_arn.value
  replica_count         = var.valkey_replica_count
}

module "auth" {
  source = "../../../modules/data/auth"

  user_pool_name     = var.user_pool_name
  custom_domain_name = var.cognito_domain_prefix
  callback_urls      = var.cognito_callback_urls
  logout_urls        = var.cognito_logout_urls
}

module "ai_pipeline" {
  source = "../../../modules/data/ai_pipeline"

  environment = var.environment
}

# ── 02-runtime이 조회할 SSM 파라미터 (dev/prod와 같은 키 이름) ──────
resource "aws_ssm_parameter" "db_endpoint" {
  name  = "${local.ssm_prefix}/data/db_endpoint"
  type  = "String"
  value = module.database_private_dns.writer_fqdn
}

resource "aws_ssm_parameter" "db_reader_endpoint" {
  name  = "${local.ssm_prefix}/data/db_reader_endpoint"
  type  = "String"
  value = module.database_private_dns.reader_fqdn
}

resource "aws_ssm_parameter" "rds_proxy_endpoint" {
  name  = "${local.ssm_prefix}/data/rds_proxy_endpoint"
  type  = "String"
  value = module.rds_proxy.proxy_endpoint
}

# 이제 dev 시크릿을 참조하지 않는다 - RDS가 자체 발급한 마스터 시크릿이다.
# 앱은 이 계정을 쓰지 않는다(서비스 계정 4개를 쓴다). 이 값은 00-init.sql 실행과
# 데이터 이관 같은 관리 작업용이다.
resource "aws_ssm_parameter" "db_master_secret_arn" {
  name  = "${local.ssm_prefix}/data/db_master_secret_arn"
  type  = "String"
  value = module.rds_postgres.master_user_secret_arn
}

# sync-github-config.sh가 GitHub Secrets 8개를 채울 때 조회한다.
# 이 경로가 생기기 전에는 마스터 시크릿 하나를 8개에 복제하고 있었다.
resource "aws_ssm_parameter" "db_service_account_secret_arn" {
  for_each = module.db_service_accounts.secret_arn_by_account

  name  = "${local.ssm_prefix}/data/db_${each.key}_secret_arn"
  type  = "String"
  value = each.value
}

resource "aws_ssm_parameter" "valkey_endpoint" {
  name  = "${local.ssm_prefix}/data/valkey_endpoint"
  type  = "String"
  value = module.cache_valkey.valkey_primary_endpoint
}

resource "aws_ssm_parameter" "valkey_reader_endpoint" {
  name  = "${local.ssm_prefix}/data/valkey_reader_endpoint"
  type  = "String"
  value = module.cache_valkey.valkey_reader_endpoint
}

resource "aws_ssm_parameter" "cognito_user_pool_id" {
  name  = "${local.ssm_prefix}/auth/user_pool_id"
  type  = "String"
  value = module.auth.user_pool_id
}

resource "aws_ssm_parameter" "cognito_user_pool_client_id" {
  name  = "${local.ssm_prefix}/auth/user_pool_client_id"
  type  = "String"
  value = module.auth.user_pool_client_id
}

resource "aws_ssm_parameter" "cognito_user_pool_arn" {
  name  = "${local.ssm_prefix}/auth/user_pool_arn"
  type  = "String"
  value = module.auth.user_pool_arn
}

resource "aws_ssm_parameter" "ai_channel" {
  for_each = local.ai_channel_ssm_values

  name  = "${local.ssm_prefix}/${each.key}"
  type  = "String"
  value = each.value
}

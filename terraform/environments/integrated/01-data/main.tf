# integrated 01-data
#
# 핵심: dev의 기존 EC2 Postgres 인스턴스를 재사용해서 그 안에 bookdb_prod를
# "추가"한다. modules/dev_tools/ec2_postgres 모듈을 다시 부르면 인스턴스가
# 통째로 새로 생겨버리므로(그 모듈은 항상 aws_instance를 resource로 만든다)
# 여기서는 그 모듈을 호출하지 않고, 기존 인스턴스를 data source로 참조한 뒤
# 그 모듈 안의 aws_ssm_association "ensure_database"와 정확히 같은 패턴을
# 복제해서 새 인스턴스를 만들지 않고 새 DB만 멱등하게 만든다.
#
# 이렇게 하면:
#   - dev의 01-data state는 전혀 건드리지 않는다 (data source만 사용)
#   - 지금 운영 중인 bookdb_dev/기존 데이터에는 영향 없음 (다른 DB를 추가할 뿐)
#   - 반복 apply해도 안전 (association 커맨드 자체가 존재 여부를 먼저 확인)

locals {
  ssm_prefix = "/${var.environment}"

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

# dev/01-data가 만든, 지금 실제로 떠 있는 그 인스턴스.
# modules/dev_tools/ec2_postgres의 tags.Name = "lion-team3-${environment}-ec2-postgres" 그대로.
data "aws_instance" "dev_postgres" {
  filter {
    name   = "tag:Name"
    values = ["lion-team3-dev-ec2-postgres"]
  }

  filter {
    name   = "instance-state-name"
    values = ["running"]
  }
}

# dev의 마스터 계정 시크릿을 그대로 재사용 (같은 인스턴스 = 같은 계정).
data "aws_ssm_parameter" "dev_db_master_secret_arn" {
  name = "/dev/data/db_master_secret_arn"
}

# ── bookdb_prod를 dev 인스턴스 안에 멱등하게 생성 ──────────────────
# modules/dev_tools/ec2_postgres/main.tf의 aws_ssm_association "ensure_database"와
# 완전히 동일한 커맨드. 그 모듈을 다시 호출하지 않고 값만 복제한 이유는 위 주석 참고.
resource "aws_ssm_association" "ensure_bookdb_prod" {
  name             = "AWS-RunShellScript"
  association_name = "lion-team3-${var.environment}-ensure-${var.database_name}"

  targets {
    key    = "InstanceIds"
    values = [data.aws_instance.dev_postgres.id]
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
  value = data.aws_instance.dev_postgres.private_dns
}

resource "aws_ssm_parameter" "db_reader_endpoint" {
  name  = "${local.ssm_prefix}/data/db_reader_endpoint"
  type  = "String"
  value = data.aws_instance.dev_postgres.private_dns
}

resource "aws_ssm_parameter" "rds_proxy_endpoint" {
  name  = "${local.ssm_prefix}/data/rds_proxy_endpoint"
  type  = "String"
  value = data.aws_instance.dev_postgres.private_dns
}

# 주의: 이 시크릿은 dev 소유 - integrated 클러스터의 prod 네임스페이스 Pod가
# 이 값을 읽으려면 그 Pod의 IRSA Role에 secretsmanager:GetSecretValue를
# 이 ARN으로 별도 부여해야 한다 (02-runtime/member_service_iam 등은 지금
# 이 권한을 갖고 있지 않음 - 배포 전 확인할 것).
resource "aws_ssm_parameter" "db_master_secret_arn" {
  name  = "${local.ssm_prefix}/data/db_master_secret_arn"
  type  = "String"
  value = data.aws_ssm_parameter.dev_db_master_secret_arn.value
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

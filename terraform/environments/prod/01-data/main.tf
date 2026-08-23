locals {
  ssm_prefix = "/${var.environment}"

  # ai/* SSM 경로 접미사를 한 곳에만 적는다 - scripts/sync-github-config.sh의
  # ssm() 호출 인자(예: "ai/ingest_channel_url")가 이 key와 정확히 같아야
  # 한다(/code-review 지적사항 - terraform과 스크립트 양쪽에 각각 하드코딩돼
  # 있으면 한쪽만 바뀌었을 때 조용히 어긋난다).
  ai_channel_ssm_values = {
    "ai/ingest_channel_arn"   = module.ai_pipeline.ingest_channel_arn
    "ai/ingest_channel_url"   = module.ai_pipeline.ingest_channel_url
    "ai/purchase_channel_arn" = module.ai_pipeline.purchase_channel_arn
    "ai/purchase_channel_url" = module.ai_pipeline.purchase_channel_url
  }
}

data "aws_ssm_parameter" "vpc_id" {
  name = "${local.ssm_prefix}/network/vpc_id"
}

data "aws_ssm_parameter" "data_subnet_ids" {
  name = "${local.ssm_prefix}/network/data_subnet_ids"
}

data "aws_ssm_parameter" "app_security_group_id" {
  name = "${local.ssm_prefix}/network/app_security_group_id"
}

data "aws_ssm_parameter" "sns_topic_arn" {
  name = "${local.ssm_prefix}/alerting/sns_topic_arn"
}

module "aurora_pg" {
  source = "../../../modules/data/aurora_pg"

  environment             = var.environment
  vpc_id                  = data.aws_ssm_parameter.vpc_id.value
  data_subnet_ids         = split(",", data.aws_ssm_parameter.data_subnet_ids.value)
  app_security_group_id   = data.aws_ssm_parameter.app_security_group_id.value
  database_name           = var.database_name
  master_username         = var.master_username
  sns_topic_arn           = data.aws_ssm_parameter.sns_topic_arn.value
  reader_count            = var.reader_count
  deletion_protection     = var.aurora_deletion_protection
  skip_final_snapshot     = var.aurora_skip_final_snapshot
  backup_retention_period = var.aurora_backup_retention_period
}

module "rds_proxy" {
  source = "../../../modules/data/rds_proxy"

  environment               = var.environment
  vpc_id                    = data.aws_ssm_parameter.vpc_id.value
  data_subnet_ids           = split(",", data.aws_ssm_parameter.data_subnet_ids.value)
  app_security_group_id     = data.aws_ssm_parameter.app_security_group_id.value
  cluster_security_group_id = module.aurora_pg.cluster_security_group_id
  aurora_cluster_identifier = module.aurora_pg.cluster_identifier
  secrets_manager_arn       = module.aurora_pg.master_user_secret_arn
}

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

# ── 02-runtime이 조회할 SSM 파라미터 ────────────────────────────────
resource "aws_ssm_parameter" "db_endpoint" {
  name  = "${local.ssm_prefix}/data/db_endpoint"
  type  = "String"
  value = module.aurora_pg.cluster_endpoint
}

resource "aws_ssm_parameter" "db_reader_endpoint" {
  name  = "${local.ssm_prefix}/data/db_reader_endpoint"
  type  = "String"
  value = module.aurora_pg.reader_endpoint
}

resource "aws_ssm_parameter" "rds_proxy_endpoint" {
  name  = "${local.ssm_prefix}/data/rds_proxy_endpoint"
  type  = "String"
  value = module.rds_proxy.proxy_endpoint
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

# member_service_iam(02-runtime)이 Cognito Admin API 권한을 이 User Pool 하나로
# 스코프하는 데 필요하다.
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

moved {
  from = aws_ssm_parameter.ai_ingest_channel_arn
  to   = aws_ssm_parameter.ai_channel["ai/ingest_channel_arn"]
}

moved {
  from = aws_ssm_parameter.ai_ingest_channel_url
  to   = aws_ssm_parameter.ai_channel["ai/ingest_channel_url"]
}

moved {
  from = aws_ssm_parameter.ai_purchase_channel_arn
  to   = aws_ssm_parameter.ai_channel["ai/purchase_channel_arn"]
}

moved {
  from = aws_ssm_parameter.ai_purchase_channel_url
  to   = aws_ssm_parameter.ai_channel["ai/purchase_channel_url"]
}

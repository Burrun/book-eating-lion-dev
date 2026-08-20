locals {
  ssm_prefix = "/${var.environment}"
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

  environment           = var.environment
  vpc_id                = data.aws_ssm_parameter.vpc_id.value
  data_subnet_ids       = split(",", data.aws_ssm_parameter.data_subnet_ids.value)
  app_security_group_id = data.aws_ssm_parameter.app_security_group_id.value
  database_name         = var.database_name
  master_username       = var.master_username
  sns_topic_arn         = data.aws_ssm_parameter.sns_topic_arn.value
  reader_count          = var.reader_count
  deletion_protection   = var.aurora_deletion_protection
  skip_final_snapshot   = var.aurora_skip_final_snapshot
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

resource "aws_ssm_parameter" "ai_ingest_channel_arn" {
  name  = "${local.ssm_prefix}/ai/ingest_channel_arn"
  type  = "String"
  value = module.ai_pipeline.ingest_channel_arn
}

resource "aws_ssm_parameter" "ai_purchase_channel_arn" {
  name  = "${local.ssm_prefix}/ai/purchase_channel_arn"
  type  = "String"
  value = module.ai_pipeline.purchase_channel_arn
}

resource "aws_ssm_parameter" "ai_purchase_channel_url" {
  name  = "${local.ssm_prefix}/ai/purchase_channel_url"
  type  = "String"
  value = module.ai_pipeline.purchase_channel_url
}

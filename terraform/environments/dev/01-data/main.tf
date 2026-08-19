locals {
  ssm_prefix = "/${var.environment}"
}

data "aws_ssm_parameter" "vpc_id" {
  name = "${local.ssm_prefix}/network/vpc_id"
}

data "aws_ssm_parameter" "app_subnet_ids" {
  name = "${local.ssm_prefix}/network/app_subnet_ids"
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

# aurora_pg 대신 - Aurora Multi-AZ는 dev에서 상시 켜두기엔 비용이 안 맞는다 (§6.3).
module "ec2_postgres" {
  source = "../../../modules/dev_tools/ec2_postgres"

  environment           = var.environment
  vpc_id                = data.aws_ssm_parameter.vpc_id.value
  app_subnet_id         = split(",", data.aws_ssm_parameter.app_subnet_ids.value)[0]
  app_security_group_id = data.aws_ssm_parameter.app_security_group_id.value
  instance_type         = var.ec2_postgres_instance_type
  database_name         = var.database_name
  master_username       = var.master_username
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

# ── 02-runtime이 조회할 SSM 파라미터 (prod와 동일한 키 이름) ────────
resource "aws_ssm_parameter" "db_endpoint" {
  name  = "${local.ssm_prefix}/data/db_endpoint"
  type  = "String"
  value = module.ec2_postgres.cluster_endpoint
}

resource "aws_ssm_parameter" "db_reader_endpoint" {
  name  = "${local.ssm_prefix}/data/db_reader_endpoint"
  type  = "String"
  value = module.ec2_postgres.reader_endpoint
}

# rds_proxy는 Aurora 전용이라 dev에서는 호출 안 함 - 같은 값을 그대로 등록해서
# 02-runtime이 환경별 분기 없이 같은 SSM 키를 읽게 한다.
resource "aws_ssm_parameter" "rds_proxy_endpoint" {
  name  = "${local.ssm_prefix}/data/rds_proxy_endpoint"
  type  = "String"
  value = module.ec2_postgres.cluster_endpoint
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

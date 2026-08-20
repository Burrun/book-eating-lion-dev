# github_oidc 모듈의 create_oidc_provider는 dev/prod 둘 다 false다 - 이 공유 계정에
# 이미 다른 프로젝트가 만들어둔 GitHub OIDC Provider가 있어서(2026-08-20 확인,
# 2025-12-23 생성) 우리 쪽에서 새로 만들 필요도 없고 만들면 충돌한다. 그래서
# dev/prod 사이에 apply 순서 의존성이 없다 - 계층 순서만 지키면 됨(00-base ->
# 01-data -> 02-runtime, TERRAFORM_STRUCTURE.md §5.1).

module "vpc" {
  source = "../../../modules/base/vpc"

  environment         = var.environment
  vpc_cidr            = var.vpc_cidr
  availability_zones  = var.availability_zones
  public_subnet_cidrs = var.public_subnet_cidrs
  app_subnet_cidrs    = var.app_subnet_cidrs
  data_subnet_cidrs   = var.data_subnet_cidrs
}

module "dns_zone" {
  source = "../../../modules/base/dns_zone"

  domain_name        = var.domain_name
  parent_zone_domain = var.parent_zone_domain
}

# us-east-1 alias 필수 — CloudFront는 us-east-1 인증서만 받는다.
module "acm_cert" {
  source = "../../../modules/base/acm_cert"
  providers = {
    aws = aws.us_east_1
  }

  domain_name     = var.domain_name
  route53_zone_id = module.dns_zone.route53_zone_id
}

# us-east-1 alias 필수 — CLOUDFRONT 스코프 WebACL은 us-east-1에서만 생성 가능.
module "waf" {
  source = "../../../modules/base/waf"
  providers = {
    aws = aws.us_east_1
  }

  name       = "lion-team3-${var.environment}"
  rate_limit = var.waf_rate_limit
}

module "storage" {
  source = "../../../modules/base/storage"

  frontend_bucket_name = var.frontend_bucket_name
  media_bucket_name    = var.media_bucket_name
}

module "container_reg" {
  source = "../../../modules/base/container_reg"

  environment   = var.environment
  service_names = var.service_names
}

module "alerting" {
  source = "../../../modules/base/alerting"

  name        = "lion-team3-${var.environment}-alerts"
  alert_email = var.alert_email
}

module "github_oidc" {
  source = "../../../modules/base/github_oidc"

  environment          = var.environment
  create_oidc_provider = false # 이미 계정에 있는 (남의) Provider를 데이터소스로 조회만 함 - 위 주석 참고
  github_org           = var.github_org
  github_repo          = var.github_repo
  ecr_repository_arns  = values(module.container_reg.repository_arns)
  # eks_cluster 모듈의 cluster_name 기본값(coalesce(var.cluster_name, "lion-team3-${var.environment}"))과
  # 반드시 같아야 한다 - 02-runtime에서 cluster_name을 override하면 여기도 같이 바꿀 것.
  eks_cluster_name = "lion-team3-${var.environment}"
}

# ── 하위 계층(01-data, 02-runtime)이 조회할 SSM 파라미터 ──────────
# TERRAFORM_STRUCTURE.md §4 "계층 간 데이터 연동 표준" 참고.
locals {
  ssm_prefix = "/${var.environment}"
}

resource "aws_ssm_parameter" "vpc_id" {
  name  = "${local.ssm_prefix}/network/vpc_id"
  type  = "String"
  value = module.vpc.vpc_id
}

resource "aws_ssm_parameter" "public_subnet_ids" {
  name  = "${local.ssm_prefix}/network/public_subnet_ids"
  type  = "StringList"
  value = join(",", module.vpc.public_subnet_ids)
}

resource "aws_ssm_parameter" "app_subnet_ids" {
  name  = "${local.ssm_prefix}/network/app_subnet_ids"
  type  = "StringList"
  value = join(",", module.vpc.app_subnet_ids)
}

resource "aws_ssm_parameter" "data_subnet_ids" {
  name  = "${local.ssm_prefix}/network/data_subnet_ids"
  type  = "StringList"
  value = join(",", module.vpc.data_subnet_ids)
}

resource "aws_ssm_parameter" "app_security_group_id" {
  name  = "${local.ssm_prefix}/network/app_security_group_id"
  type  = "String"
  value = module.vpc.app_security_group_id
}

resource "aws_ssm_parameter" "acm_certificate_arn" {
  name  = "${local.ssm_prefix}/edge/acm_certificate_arn"
  type  = "String"
  value = module.acm_cert.acm_certificate_arn
}

resource "aws_ssm_parameter" "route53_zone_id" {
  name  = "${local.ssm_prefix}/edge/route53_zone_id"
  type  = "String"
  value = module.dns_zone.route53_zone_id
}

resource "aws_ssm_parameter" "waf_web_acl_arn" {
  name  = "${local.ssm_prefix}/edge/waf_web_acl_arn"
  type  = "String"
  value = module.waf.waf_web_acl_arn
}

resource "aws_ssm_parameter" "frontend_bucket_id" {
  name  = "${local.ssm_prefix}/storage/frontend_bucket_id"
  type  = "String"
  value = module.storage.frontend_bucket_id
}

resource "aws_ssm_parameter" "frontend_bucket_arn" {
  name  = "${local.ssm_prefix}/storage/frontend_bucket_arn"
  type  = "String"
  value = module.storage.frontend_bucket_arn
}

resource "aws_ssm_parameter" "frontend_bucket_domain_name" {
  name  = "${local.ssm_prefix}/storage/frontend_bucket_domain_name"
  type  = "String"
  value = module.storage.frontend_bucket_domain_name
}

resource "aws_ssm_parameter" "media_bucket_id" {
  name  = "${local.ssm_prefix}/storage/media_bucket_id"
  type  = "String"
  value = module.storage.media_bucket_id
}

resource "aws_ssm_parameter" "media_bucket_arn" {
  name  = "${local.ssm_prefix}/storage/media_bucket_arn"
  type  = "String"
  value = module.storage.media_bucket_arn
}

resource "aws_ssm_parameter" "sns_topic_arn" {
  name  = "${local.ssm_prefix}/alerting/sns_topic_arn"
  type  = "String"
  value = module.alerting.sns_topic_arn
}

resource "aws_ssm_parameter" "github_actions_role_arn" {
  name  = "${local.ssm_prefix}/ci/github_actions_role_arn"
  type  = "String"
  value = module.github_oidc.github_actions_role_arn
}

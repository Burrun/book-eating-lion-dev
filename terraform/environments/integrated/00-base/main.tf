# integrated 00-base
#
# 목적: dev/prod를 하나의 EKS 클러스터에서 namespace로만 나눠 운영하는
# "integrated" 환경. 존재 이유가 비용 절감이므로 VPC/서브넷/보안그룹은
# 새로 만들지 않고 dev/00-base가 이미 만든 걸 그대로 참조만 한다
# (아래 data.aws_ssm_parameter.dev_* 참고). 새로 만드는 건:
#   - prod 도메인(book.ajttk.com)용 Route53 Zone / ACM 인증서 / WAF
#     (dev.ajttk.com은 dev/00-base 소유 그대로 - 여기서 안 건드림.
#      dev 도메인을 이 클러스터로 옮기는 건 별도의, 트래픽 컷오버가
#      필요한 later 작업으로 의도적으로 남겨둠)
#   - prod 프론트엔드/미디어 S3 버킷
#   - integrated 전용 ECR / GitHub OIDC Role
# SNS(알림)는 새로 안 만들고 dev 것을 재사용한다 - 알림 채널 하나 더
# 만들 실익이 없음.
#
# 01-data/02-runtime이 dev/prod와 완전히 같은 코드 패턴(자기 환경의
# SSM prefix만 읽는다)을 쓸 수 있도록, dev에서 가져온 네트워크 값도
# 아래에서 /integrated/network/* 로 "복사"해 둔다 - 리소스를 새로
# 만드는 게 아니라 값을 재발행하는 것뿐이다.

data "aws_ssm_parameter" "dev_vpc_id" {
  name = "/dev/network/vpc_id"
}

# github_oidc의 extra_ssm_read_prefixes/extra_frontend_bucket_arns/extra_media_bucket_arns용.
# dev의 실제 데이터/프론트엔드는 마이그레이션 대상이 아니라서(01-data/00-base 그대로
# 유지) integrated role이 이 값들에도 접근할 수 있어야 dev를 이 클러스터에서
# 서빙할 수 있다 (2026-08-26 Main CD #65 AccessDenied 사고로 발견함).
data "aws_ssm_parameter" "dev_frontend_bucket_arn" {
  name = "/dev/storage/frontend_bucket_arn"
}

data "aws_ssm_parameter" "dev_media_bucket_arn" {
  name = "/dev/storage/media_bucket_arn"
}

data "aws_ssm_parameter" "dev_public_subnet_ids" {
  name = "/dev/network/public_subnet_ids"
}

data "aws_ssm_parameter" "dev_app_subnet_ids" {
  name = "/dev/network/app_subnet_ids"
}

data "aws_ssm_parameter" "dev_data_subnet_ids" {
  name = "/dev/network/data_subnet_ids"
}

data "aws_ssm_parameter" "dev_app_security_group_id" {
  name = "/dev/network/app_security_group_id"
}

data "aws_ssm_parameter" "dev_sns_topic_arn" {
  name = "/dev/alerting/sns_topic_arn"
}

# ── prod 도메인 (book.ajttk.com) ──────────────────────────────────
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

module "github_oidc" {
  source = "../../../modules/base/github_oidc"

  environment          = var.environment
  create_oidc_provider = false # 계정 공유 Provider 재사용 - dev/prod와 동일한 이유
  github_org           = var.github_org
  github_repo          = var.github_repo
  ecr_repository_arns  = values(module.container_reg.repository_arns)
  frontend_bucket_arn  = module.storage.frontend_bucket_arn
  media_bucket_arn     = module.storage.media_bucket_arn
  # dev 배포도 이 role(github-actions-lion-team3-integrated)로 도는데, dev의
  # DB/프론트엔드 버킷은 마이그레이션 대상이 아니라 여전히 /dev/* 소유다.
  extra_ssm_read_prefixes    = ["dev"]
  extra_frontend_bucket_arns = [data.aws_ssm_parameter.dev_frontend_bucket_arn.value]
  extra_media_bucket_arns    = [data.aws_ssm_parameter.dev_media_bucket_arn.value]
  # 02-runtime의 eks_cluster 기본 이름(lion-team3-${environment})과 반드시 같아야 한다.
  eks_cluster_name = "lion-team3-${var.environment}"
}

locals {
  ssm_prefix = "/${var.environment}"
}

# ── 네트워크: 새로 만들지 않고 dev 값을 그대로 재발행 ──────────────
resource "aws_ssm_parameter" "vpc_id" {
  name  = "${local.ssm_prefix}/network/vpc_id"
  type  = "String"
  value = data.aws_ssm_parameter.dev_vpc_id.value
}

resource "aws_ssm_parameter" "public_subnet_ids" {
  name  = "${local.ssm_prefix}/network/public_subnet_ids"
  type  = "StringList"
  value = data.aws_ssm_parameter.dev_public_subnet_ids.value
}

resource "aws_ssm_parameter" "app_subnet_ids" {
  name  = "${local.ssm_prefix}/network/app_subnet_ids"
  type  = "StringList"
  value = data.aws_ssm_parameter.dev_app_subnet_ids.value
}

resource "aws_ssm_parameter" "data_subnet_ids" {
  name  = "${local.ssm_prefix}/network/data_subnet_ids"
  type  = "StringList"
  value = data.aws_ssm_parameter.dev_data_subnet_ids.value
}

resource "aws_ssm_parameter" "app_security_group_id" {
  name  = "${local.ssm_prefix}/network/app_security_group_id"
  type  = "String"
  value = data.aws_ssm_parameter.dev_app_security_group_id.value
}

# ── 새로 만든 것들 ─────────────────────────────────────────────
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

# ── 알림: 새로 안 만들고 dev SNS 재사용 ────────────────────────────
resource "aws_ssm_parameter" "sns_topic_arn" {
  name  = "${local.ssm_prefix}/alerting/sns_topic_arn"
  type  = "String"
  value = data.aws_ssm_parameter.dev_sns_topic_arn.value
}

resource "aws_ssm_parameter" "github_actions_role_arn" {
  name  = "${local.ssm_prefix}/ci/github_actions_role_arn"
  type  = "String"
  value = module.github_oidc.github_actions_role_arn
}

locals {
  ssm_prefix = "/${var.environment}"
}

data "aws_ssm_parameter" "alb_dns_name" {
  name = "${local.ssm_prefix}/runtime/alb_dns_name"
}

data "aws_ssm_parameter" "route53_zone_id" {
  name = "${local.ssm_prefix}/edge/route53_zone_id"
}

data "aws_ssm_parameter" "acm_certificate_arn" {
  name = "${local.ssm_prefix}/edge/acm_certificate_arn"
}

data "aws_ssm_parameter" "waf_web_acl_arn" {
  name = "${local.ssm_prefix}/edge/waf_web_acl_arn"
}

data "aws_ssm_parameter" "frontend_bucket_id" {
  name = "${local.ssm_prefix}/storage/frontend_bucket_id"
}

data "aws_ssm_parameter" "frontend_bucket_arn" {
  name = "${local.ssm_prefix}/storage/frontend_bucket_arn"
}

data "aws_ssm_parameter" "frontend_bucket_domain_name" {
  name = "${local.ssm_prefix}/storage/frontend_bucket_domain_name"
}

# CloudFront와 공개 Route 53 레코드는 EKS/ALB 수명주기와 분리한다.
module "edge_routing" {
  source = "../../../modules/compute/edge_routing"

  environment                 = var.environment
  domain_name                 = var.domain_name
  alb_dns_name                = data.aws_ssm_parameter.alb_dns_name.value
  route53_zone_id             = data.aws_ssm_parameter.route53_zone_id.value
  acm_certificate_arn         = data.aws_ssm_parameter.acm_certificate_arn.value
  waf_web_acl_arn             = data.aws_ssm_parameter.waf_web_acl_arn.value
  frontend_bucket_id          = data.aws_ssm_parameter.frontend_bucket_id.value
  frontend_bucket_arn         = data.aws_ssm_parameter.frontend_bucket_arn.value
  frontend_bucket_domain_name = data.aws_ssm_parameter.frontend_bucket_domain_name.value
}

resource "aws_ssm_parameter" "cloudfront_distribution_id" {
  name      = "${local.ssm_prefix}/edge/cloudfront_distribution_id"
  type      = "String"
  value     = module.edge_routing.cloudfront_distribution_id
  overwrite = true
}

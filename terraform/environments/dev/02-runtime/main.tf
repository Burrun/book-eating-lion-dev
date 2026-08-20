locals {
  ssm_prefix = "/${var.environment}"
}

data "aws_ssm_parameter" "vpc_id" {
  name = "${local.ssm_prefix}/network/vpc_id"
}

data "aws_ssm_parameter" "app_subnet_ids" {
  name = "${local.ssm_prefix}/network/app_subnet_ids"
}

data "aws_ssm_parameter" "public_subnet_ids" {
  name = "${local.ssm_prefix}/network/public_subnet_ids"
}

data "aws_ssm_parameter" "sns_topic_arn" {
  name = "${local.ssm_prefix}/alerting/sns_topic_arn"
}

data "aws_ssm_parameter" "github_actions_role_arn" {
  name = "${local.ssm_prefix}/ci/github_actions_role_arn"
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

data "aws_ssm_parameter" "ai_ingest_channel_arn" {
  name = "${local.ssm_prefix}/ai/ingest_channel_arn"
}

# ── 1. EKS 클러스터 (최초 apply 시 -target으로 먼저 만들 것) ────────
module "eks_cluster" {
  source = "../../../modules/compute/eks_cluster"

  environment             = var.environment
  vpc_id                  = data.aws_ssm_parameter.vpc_id.value
  app_subnet_ids          = split(",", data.aws_ssm_parameter.app_subnet_ids.value)
  cluster_version         = var.cluster_version
  sns_topic_arn           = data.aws_ssm_parameter.sns_topic_arn.value
  github_actions_role_arn = data.aws_ssm_parameter.github_actions_role_arn.value
}

# ── 2. Karpenter ────────────────────────────────────────────────
module "karpenter" {
  source = "../../../modules/compute/karpenter"
  providers = {
    helm       = helm
    kubernetes = kubernetes
  }

  environment            = var.environment
  cluster_name           = module.eks_cluster.cluster_name
  cluster_endpoint       = module.eks_cluster.cluster_endpoint
  oidc_provider_arn      = module.eks_cluster.oidc_provider_arn
  oidc_provider_url      = module.eks_cluster.oidc_provider_url
  vpc_id                 = data.aws_ssm_parameter.vpc_id.value
  app_subnet_ids         = split(",", data.aws_ssm_parameter.app_subnet_ids.value)
  node_security_group_id = module.eks_cluster.cluster_security_group_id

  depends_on = [module.eks_cluster]
}

# ── 3. ingress-nginx + AWS Load Balancer Controller ────────────
module "ingress_alb" {
  source = "../../../modules/compute/ingress_alb"
  providers = {
    helm       = helm
    kubernetes = kubernetes
  }

  environment       = var.environment
  cluster_name      = module.eks_cluster.cluster_name
  oidc_provider_arn = module.eks_cluster.oidc_provider_arn
  oidc_provider_url = module.eks_cluster.oidc_provider_url
  vpc_id            = data.aws_ssm_parameter.vpc_id.value
  public_subnet_ids = split(",", data.aws_ssm_parameter.public_subnet_ids.value)
  aws_region        = var.aws_region

  depends_on = [module.eks_cluster, module.karpenter]
}

# ── 4. CloudFront + Route53 (ALB가 준비된 뒤에만 가능) ─────────────
module "edge_routing" {
  source = "../../../modules/compute/edge_routing"

  environment                 = var.environment
  domain_name                 = var.domain_name
  alb_dns_name                = module.ingress_alb.alb_dns_name
  route53_zone_id             = data.aws_ssm_parameter.route53_zone_id.value
  acm_certificate_arn         = data.aws_ssm_parameter.acm_certificate_arn.value
  waf_web_acl_arn             = data.aws_ssm_parameter.waf_web_acl_arn.value
  frontend_bucket_id          = data.aws_ssm_parameter.frontend_bucket_id.value
  frontend_bucket_arn         = data.aws_ssm_parameter.frontend_bucket_arn.value
  frontend_bucket_domain_name = data.aws_ssm_parameter.frontend_bucket_domain_name.value
}

# ── 5. AI 서비스 IRSA (ingress_alb와 무관하게 나란히 적용 가능) ───
module "ai_service_iam" {
  source = "../../../modules/compute/ai_service_iam"

  environment                  = var.environment
  oidc_provider_arn            = module.eks_cluster.oidc_provider_arn
  oidc_provider_url            = module.eks_cluster.oidc_provider_url
  ingest_channel_arn           = data.aws_ssm_parameter.ai_ingest_channel_arn.value
  recommendation_index_arn     = null # S3 Vectors provider 지원 전까지 null
  purchased_book_rag_index_arn = null
  bedrock_model_arns           = var.bedrock_model_arns
}

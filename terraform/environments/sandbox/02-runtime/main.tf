locals {
  # dev의 00-base/01-data가 publish한 값을 읽는다 - 이 레이어가 만드는 리소스
  # 자체의 이름/태그(var.environment = "sandbox")와는 별개다.
  read_ssm_prefix = "/${var.base_environment}"

  # eks_cluster/karpenter 양쪽에 정확히 같은 값을 전달한다(dev/02-runtime의 판단을 그대로 따름).
  system_pool_taint_key    = "CriticalAddonsOnly"
  system_pool_taint_value  = "true"
  system_pool_taint_effect = "NoSchedule"
}

data "aws_ssm_parameter" "vpc_id" {
  name = "${local.read_ssm_prefix}/network/vpc_id"
}

data "aws_ssm_parameter" "app_subnet_ids" {
  name = "${local.read_ssm_prefix}/network/app_subnet_ids"
}

data "aws_ssm_parameter" "app_security_group_id" {
  name = "${local.read_ssm_prefix}/network/app_security_group_id"
}

data "aws_ssm_parameter" "public_subnet_ids" {
  name = "${local.read_ssm_prefix}/network/public_subnet_ids"
}

data "aws_ssm_parameter" "sns_topic_arn" {
  name = "${local.read_ssm_prefix}/alerting/sns_topic_arn"
}

data "aws_ssm_parameter" "github_actions_role_arn" {
  name = "${local.read_ssm_prefix}/ci/github_actions_role_arn"
}

data "aws_ssm_parameter" "ai_ingest_channel_arn" {
  name = "${local.read_ssm_prefix}/ai/ingest_channel_arn"
}

data "aws_ssm_parameter" "ai_purchase_channel_arn" {
  name = "${local.read_ssm_prefix}/ai/purchase_channel_arn"
}

# ── 1. EKS 클러스터 (최초 apply 시 -target으로 먼저 만들 것) ────────
module "eks_cluster" {
  source = "../../../modules/compute/eks_cluster"

  environment              = var.environment
  vpc_id                   = data.aws_ssm_parameter.vpc_id.value
  app_subnet_ids           = split(",", data.aws_ssm_parameter.app_subnet_ids.value)
  cluster_version          = var.cluster_version
  sns_topic_arn            = data.aws_ssm_parameter.sns_topic_arn.value
  github_actions_role_arn  = data.aws_ssm_parameter.github_actions_role_arn.value
  admin_principal_arns     = var.admin_principal_arns
  system_pool_taint_key    = local.system_pool_taint_key
  system_pool_taint_value  = local.system_pool_taint_value
  system_pool_taint_effect = local.system_pool_taint_effect
}

# ── 2. Karpenter ────────────────────────────────────────────────
module "karpenter" {
  source = "../../../modules/compute/karpenter"
  providers = {
    helm       = helm
    kubernetes = kubernetes
  }

  environment              = var.environment
  cluster_name             = module.eks_cluster.cluster_name
  cluster_endpoint         = module.eks_cluster.cluster_endpoint
  oidc_provider_arn        = module.eks_cluster.oidc_provider_arn
  oidc_provider_url        = module.eks_cluster.oidc_provider_url
  vpc_id                   = data.aws_ssm_parameter.vpc_id.value
  app_subnet_ids           = split(",", data.aws_ssm_parameter.app_subnet_ids.value)
  node_security_group_id   = module.eks_cluster.cluster_security_group_id
  app_security_group_id    = data.aws_ssm_parameter.app_security_group_id.value
  system_pool_taint_key    = local.system_pool_taint_key
  system_pool_taint_effect = local.system_pool_taint_effect

  depends_on = [module.eks_cluster]
}

# ── 3. ingress-nginx + AWS Load Balancer Controller ────────────
# dev/02-runtime과 달리 edge_routing(CloudFront/Route53/커스텀 도메인)은 안 만든다 -
# 이 레이어의 목적은 "destroy+apply가 깨끗이 되는가" 검증이지 실제 트래픽을 받는 게
# 아니라서, ALB의 자체 DNS 이름으로 충분하다. 도메인 하나를 dev와 공유해서 나누는
# 복잡도를 낼 이유가 없다.
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

# ── 4. AI 서비스 IRSA ──────────────────────────────────────────
module "ai_service_iam" {
  source = "../../../modules/compute/ai_service_iam"

  environment                  = var.environment
  oidc_provider_arn            = module.eks_cluster.oidc_provider_arn
  oidc_provider_url            = module.eks_cluster.oidc_provider_url
  ingest_channel_arn           = data.aws_ssm_parameter.ai_ingest_channel_arn.value
  purchase_channel_arn         = data.aws_ssm_parameter.ai_purchase_channel_arn.value
  recommendation_index_arn     = var.recommendation_index_arn
  purchased_book_rag_index_arn = var.purchased_book_rag_index_arn
  bedrock_model_arns           = var.bedrock_model_arns
}

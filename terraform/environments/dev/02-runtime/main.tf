locals {
  ssm_prefix = "/${var.environment}"

  # CoreDNS/Karpenter 컨트롤러 전용 시스템 노드그룹 taint - eks_cluster(taint를
  # 붙이는 쪽)와 karpenter(그 taint를 견뎌야 하는 컨트롤러) 양쪽에 정확히 같은
  # 값을 전달해서, 각 모듈에 따로 하드코딩돼 있어 나중에 한쪽만 바뀌는
  # 불일치를 막는다(/code-review 지적사항).
  system_pool_taint_key    = "CriticalAddonsOnly"
  system_pool_taint_value  = "true"
  system_pool_taint_effect = "NoSchedule" # k8s 표기 - eks_cluster 모듈이 AWS API 표기로 변환
}

data "aws_ssm_parameter" "vpc_id" {
  name = "${local.ssm_prefix}/network/vpc_id"
}

data "aws_ssm_parameter" "app_subnet_ids" {
  name = "${local.ssm_prefix}/network/app_subnet_ids"
}

data "aws_ssm_parameter" "app_security_group_id" {
  name = "${local.ssm_prefix}/network/app_security_group_id"
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
  count = var.enable_edge_routing ? 1 : 0
  name  = "${local.ssm_prefix}/edge/route53_zone_id"
}

data "aws_ssm_parameter" "acm_certificate_arn" {
  count = var.enable_edge_routing ? 1 : 0
  name  = "${local.ssm_prefix}/edge/acm_certificate_arn"
}

data "aws_ssm_parameter" "waf_web_acl_arn" {
  count = var.enable_edge_routing ? 1 : 0
  name  = "${local.ssm_prefix}/edge/waf_web_acl_arn"
}

data "aws_ssm_parameter" "frontend_bucket_id" {
  count = var.enable_edge_routing ? 1 : 0
  name  = "${local.ssm_prefix}/storage/frontend_bucket_id"
}

data "aws_ssm_parameter" "frontend_bucket_arn" {
  count = var.enable_edge_routing ? 1 : 0
  name  = "${local.ssm_prefix}/storage/frontend_bucket_arn"
}

data "aws_ssm_parameter" "frontend_bucket_domain_name" {
  count = var.enable_edge_routing ? 1 : 0
  name  = "${local.ssm_prefix}/storage/frontend_bucket_domain_name"
}

data "aws_ssm_parameter" "ai_ingest_channel_arn" {
  name = "${local.ssm_prefix}/ai/ingest_channel_arn"
}

data "aws_ssm_parameter" "ai_purchase_channel_arn" {
  name = "${local.ssm_prefix}/ai/purchase_channel_arn"
}

data "aws_ssm_parameter" "cognito_user_pool_arn" {
  name = "${local.ssm_prefix}/auth/user_pool_arn"
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
# ⚠️ integrated/02-runtime 쪽에서 enable_dev_cutover=true로 dev.ajttk.com을
# 이 환경 대신 integrated 클러스터로 컷오버해둔 상태라면, 이 모듈을 다시
# apply하면 안 된다 - 같은 Route53 레코드(dev.ajttk.com, www.dev.ajttk.com)를
# 두 tfstate가 동시에 소유하려고 해서 충돌한다(AWS가 CREATE 액션을 거부).
#
# 분리 클러스터 모드(이 환경)로 되돌리려면 순서를 지킬 것:
#   1. integrated/02-runtime에서 enable_dev_cutover=false로 바꾸고 apply
#      (module.edge_routing_dev가 destroy되어 레코드 소유권을 반납)
#   2. 그다음 이 환경(dev/02-runtime)을 apply
#
# 통합 모드와 분리 클러스터 모드는 둘 다 계속 유효한 운영 방식이다 -
# 이 모듈이 폐기된 건 아니고, 동시에 같은 도메인을 두 곳에서 소유할 수만
# 없는 것뿐이다.
module "edge_routing" {
  count  = var.enable_edge_routing ? 1 : 0
  source = "../../../modules/compute/edge_routing"

  environment                 = var.environment
  domain_name                 = var.domain_name
  alb_dns_name                = module.ingress_alb.alb_dns_name
  route53_zone_id             = data.aws_ssm_parameter.route53_zone_id[0].value
  acm_certificate_arn         = data.aws_ssm_parameter.acm_certificate_arn[0].value
  waf_web_acl_arn             = data.aws_ssm_parameter.waf_web_acl_arn[0].value
  frontend_bucket_id          = data.aws_ssm_parameter.frontend_bucket_id[0].value
  frontend_bucket_arn         = data.aws_ssm_parameter.frontend_bucket_arn[0].value
  frontend_bucket_domain_name = data.aws_ssm_parameter.frontend_bucket_domain_name[0].value
}

# CD가 CloudFront 재생성 후 GitHub Variable을 수동 갱신하지 않고 실행 시점에
# 최신 배포 ID를 찾을 수 있게 Parameter Store에 발행한다.
resource "aws_ssm_parameter" "cloudfront_distribution_id" {
  count = var.enable_edge_routing ? 1 : 0
  name  = "${local.ssm_prefix}/edge/cloudfront_distribution_id"
  type  = "String"
  value = module.edge_routing[0].cloudfront_distribution_id
  # 기존 수동 등록 값을 첫 apply에서 Terraform 관리로 인수한다.
  overwrite = true
}

# integrated 런타임이 dev 도메인을 인수할 때 순서를 강제하기 위한 핸드오프 표식.
# dev 엣지를 반납하는 apply가 먼저 완료되어야 integrated apply가 진행된다.
resource "aws_ssm_parameter" "edge_ownership_handoff" {
  name      = "/dev/edge/ownership_handoff"
  type      = "String"
  value     = var.enable_edge_routing ? "split-owned" : "integrated-ready"
  overwrite = true
}

# ── 5. AI 서비스 IRSA (ingress_alb와 무관하게 나란히 적용 가능) ───
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

# CI(scripts/sync-github-config.sh → main-cd.yml)가 k8s/ai/serviceaccount.yaml의
# eks.amazonaws.com/role-arn 애노테이션을 채우려면 이 값을 SSM으로 받아야 한다.
# 이게 없으면 ai-rag/ai-bot이 default ServiceAccount로 뜨고, IRSA 자격증명이
# 없어 AWS SDK(BedrockRuntimeClient 등) 빈 자격증명 체인으로 기동에 실패한다.
resource "aws_ssm_parameter" "ai_service_irsa_arn" {
  name  = "${local.ssm_prefix}/ai/service_irsa_arn"
  type  = "String"
  value = module.ai_service_iam.ai_service_irsa_arn
}

# ── 6. member-service IRSA (Cognito Admin API 호출용) ─────────────
# 원래 member-service는 IRSA가 아예 없어 default ServiceAccount로 떴고,
# CognitoAuthClient의 adminCreateUser/adminInitiateAuth 호출이 SdkClientException
# (자격증명 체인 전부 빈 상태)으로 죽어 회원가입/로그인이 500이었다
# (2026-08-23 dev 실배포에서 실제로 겪음).
module "member_service_iam" {
  source = "../../../modules/compute/member_service_iam"

  environment       = var.environment
  oidc_provider_arn = module.eks_cluster.oidc_provider_arn
  oidc_provider_url = module.eks_cluster.oidc_provider_url
  user_pool_arn     = data.aws_ssm_parameter.cognito_user_pool_arn.value
}

# CI가 k8s/member/serviceaccount.yaml의 eks.amazonaws.com/role-arn 애노테이션을
# 채우려면 이 값을 SSM으로 받아야 한다. ai_service_irsa_arn과 동일한 이유.
resource "aws_ssm_parameter" "member_service_irsa_arn" {
  name  = "${local.ssm_prefix}/member/service_irsa_arn"
  type  = "String"
  value = module.member_service_iam.member_service_irsa_arn
}

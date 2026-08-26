# integrated 02-runtime
#
# 여기서부터가 진짜 새로 만드는 부분이다 - EKS 클러스터 하나를 새로 띄우고,
# dev/prod 워크로드를 namespace로만 나눠 그 위에서 돌린다(네임스페이스
# 자체는 이 Terraform이 아니라 k8s manifest/CD 쪽에서 나눔).
#
# edge_routing은 prod 도메인(book.ajttk.com) 하나만 여기서 연결한다.
# dev.ajttk.com을 이 클러스터로 옮기는 건(=지금 운영 중인 dev EKS/CloudFront를
# 이 클러스터로 컷오버하는 것) 트래픽이 끊기지 않게 별도로 계획/실행해야 하는
# 작업이라 의도적으로 여기 포함하지 않았다. 지금 단계에서 이 파일이 하는 일은
# 100% 추가(additive)이고 dev의 현재 운영 인프라를 전혀 건드리지 않는다.

locals {
  ssm_prefix = "/${var.environment}"

  system_pool_taint_key    = "CriticalAddonsOnly"
  system_pool_taint_value  = "true"
  system_pool_taint_effect = "NoSchedule"
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
# dev/prod 두 namespace를 이 하나의 ALB/NLB + ingress-nginx가 같이 받는다 -
# 실제 분리는 Ingress의 host 규칙(dev.ajttk.com / book.ajttk.com)으로 한다.
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

# ── 4. CloudFront + Route53 - prod 도메인만 (book.ajttk.com) ───────
# dev.ajttk.com은 여기서 안 만든다 - 파일 상단 주석 참고.
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

# ── 4b. CloudFront + Route53 - dev 임시 컷오버 (enable_dev_cutover=true일 때만) ──
# dev.ajttk.com을 dev의 기존 클러스터 대신 이 integrated 클러스터로 붙인다.
#
# ⚠️ 주의: dev.ajttk.com Route53 레코드는 지금 dev/02-runtime state가
# 소유하고 있다. 이 모듈을 켜기 전에 dev/02-runtime의 edge_routing을
# 먼저 destroy(또는 주석 처리+apply)하지 않으면, 두 state가 같은 레코드를
# 두고 충돌한다 - 나중에 dev/02-runtime을 apply하는 순간 drift로 인식해서
# 레코드를 dev 쪽 ALB로 되돌려버려 컷오버가 조용히 풀린다.
# 되돌릴 때도 순서 반대로: 이 모듈 먼저 destroy(-target) → dev/02-runtime apply.
data "aws_ssm_parameter" "dev_route53_zone_id" {
  count = var.enable_dev_cutover ? 1 : 0
  name  = "/dev/edge/route53_zone_id"
}

data "aws_ssm_parameter" "dev_acm_certificate_arn" {
  count = var.enable_dev_cutover ? 1 : 0
  name  = "/dev/edge/acm_certificate_arn"
}

data "aws_ssm_parameter" "dev_waf_web_acl_arn" {
  count = var.enable_dev_cutover ? 1 : 0
  name  = "/dev/edge/waf_web_acl_arn"
}

data "aws_ssm_parameter" "dev_frontend_bucket_id" {
  count = var.enable_dev_cutover ? 1 : 0
  name  = "/dev/storage/frontend_bucket_id"
}

data "aws_ssm_parameter" "dev_frontend_bucket_arn" {
  count = var.enable_dev_cutover ? 1 : 0
  name  = "/dev/storage/frontend_bucket_arn"
}

data "aws_ssm_parameter" "dev_frontend_bucket_domain_name" {
  count = var.enable_dev_cutover ? 1 : 0
  name  = "/dev/storage/frontend_bucket_domain_name"
}

module "edge_routing_dev" {
  count  = var.enable_dev_cutover ? 1 : 0
  source = "../../../modules/compute/edge_routing"

  environment                 = "dev"
  domain_name                 = var.dev_domain_name
  alb_dns_name                = module.ingress_alb.alb_dns_name
  route53_zone_id             = data.aws_ssm_parameter.dev_route53_zone_id[0].value
  acm_certificate_arn         = data.aws_ssm_parameter.dev_acm_certificate_arn[0].value
  waf_web_acl_arn             = data.aws_ssm_parameter.dev_waf_web_acl_arn[0].value
  frontend_bucket_id          = data.aws_ssm_parameter.dev_frontend_bucket_id[0].value
  frontend_bucket_arn         = data.aws_ssm_parameter.dev_frontend_bucket_arn[0].value
  frontend_bucket_domain_name = data.aws_ssm_parameter.dev_frontend_bucket_domain_name[0].value

  depends_on = [module.ingress_alb]
}

# ── 5. AI 서비스 IRSA ───────────────────────────────────────────
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

resource "aws_ssm_parameter" "ai_service_irsa_arn" {
  name  = "${local.ssm_prefix}/ai/service_irsa_arn"
  type  = "String"
  value = module.ai_service_iam.ai_service_irsa_arn
}

# ── 6. member-service IRSA ──────────────────────────────────────
module "member_service_iam" {
  source = "../../../modules/compute/member_service_iam"

  environment       = var.environment
  oidc_provider_arn = module.eks_cluster.oidc_provider_arn
  oidc_provider_url = module.eks_cluster.oidc_provider_url
  user_pool_arn     = data.aws_ssm_parameter.cognito_user_pool_arn.value
}

resource "aws_ssm_parameter" "member_service_irsa_arn" {
  name  = "${local.ssm_prefix}/member/service_irsa_arn"
  type  = "String"
  value = module.member_service_iam.member_service_irsa_arn
}

# ── 7. apply 후 GitHub Actions 자동 실행 (trigger_github_actions=true일 때만) ──
# apply 실행한 로컬 PC에서 `gh workflow run`을 쏜다. gh CLI 설치 + 로그인 필요.
resource "null_resource" "trigger_github_actions" {
  count = var.trigger_github_actions ? 1 : 0

  triggers = {
    always_run = timestamp()
  }

  provisioner "local-exec" {
    command = "gh workflow run ${var.github_actions_workflow_file} --repo ${var.github_org}/${var.github_repo} --ref main"
  }

  depends_on = [
    module.eks_cluster,
    module.karpenter,
    module.ingress_alb,
    module.edge_routing,
  ]
}

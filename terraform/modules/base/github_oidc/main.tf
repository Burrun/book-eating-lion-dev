# OIDC Provider는 계정당 URL 하나에 유일한 전역 리소스다 — dev/prod 둘 다 이 모듈을
# 호출하지만 Provider 자체는 딱 한 환경(create_oidc_provider = true)에서만 만든다.
# 나머지 환경은 데이터소스로 기존 것을 조회해서 쓴다.

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

data "tls_certificate" "github" {
  count = var.create_oidc_provider ? 1 : 0
  url   = "https://token.actions.githubusercontent.com/.well-known/openid-configuration"
}

resource "aws_iam_openid_connect_provider" "github" {
  count           = var.create_oidc_provider ? 1 : 0
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github[0].certificates[0].sha1_fingerprint]
}

data "aws_iam_openid_connect_provider" "github" {
  count = var.create_oidc_provider ? 0 : 1
  url   = "https://token.actions.githubusercontent.com"
}

locals {
  oidc_provider_arn = var.create_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : data.aws_iam_openid_connect_provider.github[0].arn
}

# 트러스트 정책의 sub 조건을 이 리포지토리로 제한한다 — 다른 리포/포크가 이 역할을 못 쓰게.
# IAM Role은 환경별로 따로 둔다(권한 범위가 환경마다 다를 수 있어서) - 이름에 environment를 넣어 충돌 방지.
data "aws_iam_policy_document" "trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_org}/${var.github_repo}:*"]
    }
  }
}

resource "aws_iam_role" "github_actions" {
  name               = "github-actions-lion-team3-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.trust.json
}

data "aws_iam_policy_document" "permissions" {
  statement {
    sid       = "EcrAuth"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid    = "EcrPush"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
    ]
    resources = var.ecr_repository_arns
  }

  # kubeconfig 구성을 위한 읽기 권한. 실제 배포 권한은 K8s RBAC가 따로 통제한다.
  # DescribeCluster는 02-runtime을 참조하지 않고도(위 §변수 설명 참고) 리소스를
  # 좁힐 수 있다. 다만 이 모듈이 eks_cluster 모듈의 네이밍 규칙을 스스로 다시
  # 추측하면 두 모듈이 각자 따로 문자열을 하드코딩하게 돼 한쪽만 바뀌어도 조용히
  # 어긋난다(리뷰에서 지적됨) - 그래서 이름 자체는 var.eks_cluster_name로 환경
  # 계층(00-base main.tf)에서 명시적으로 받는다.
  statement {
    sid       = "EksDescribeCluster"
    effect    = "Allow"
    actions   = ["eks:DescribeCluster"]
    resources = ["arn:aws:eks:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:cluster/${var.eks_cluster_name}"]
  }

  statement {
    sid       = "EksListClusters"
    effect    = "Allow"
    actions   = ["eks:ListClusters"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_actions" {
  name   = "github-actions-deploy"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.permissions.json
}

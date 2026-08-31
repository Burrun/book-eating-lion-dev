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
      # GitHub이 repojacking 방지를 위해 sub claim에 불변 숫자 ID를 붙이는 신규
      # 형식(repo:OWNER@id/REPO@id:*)을 기본값으로 전환했다(2026-08-20). 신규/구
      # 형식을 둘 다 허용해 GitHub이 포맷을 또 바꾸거나 일부 이벤트에서 예전
      # 형식을 쓰는 경우에도 깨지지 않게 한다.
      values = [
        "repo:${var.github_org}/${var.github_repo}:*",
        "repo:${var.github_org}@*/${var.github_repo}@*:*",
      ]
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
      "ecr:DescribeImages", # 재실행 시 SHA 태그가 이미 push됐는지 확인하는 데 씀 (.github/workflows/main-cd.yml)
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

  # CD 설정 로더가 VPC ID에 대응하는 CIDR을 보안 설정에 주입한다.
  # DescribeVpcs는 AWS에서 리소스 수준 권한을 지원하지 않는다.
  statement {
    sid       = "Ec2DescribeVpcs"
    effect    = "Allow"
    actions   = ["ec2:DescribeVpcs"]
    resources = ["*"]
  }

  # CD가 네트워크, 스토리지, 인증, 메시징, 데이터, edge 설정을 SSM에서 읽는다.
  # 환경 경로 전체로 제한해 dev 역할이 prod 파라미터를 읽거나 그 반대가 되지
  # 않게 한다. integrated 역할은 같은 클러스터의 dev 배포를 위해 /dev/*도 읽는다.
  statement {
    sid    = "ReadEnvironmentDataParameters"
    effect = "Allow"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
    ]
    resources = concat(
      ["arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/${var.environment}/*"],
      [
        for prefix in var.extra_ssm_read_prefixes :
        "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/${prefix}/*"
      ]
    )
  }

  # Frontend → S3 & CloudFront 잡의 `aws s3 sync --delete`용. ListBucket은 버킷
  # 자체, 나머지는 객체(/*) 스코프.
  statement {
    sid     = "FrontendBucketList"
    effect  = "Allow"
    actions = ["s3:ListBucket"]
    resources = concat(
      [var.frontend_bucket_arn, var.media_bucket_arn],
      var.extra_frontend_bucket_arns,
      var.extra_media_bucket_arns,
    )
  }

  statement {
    sid    = "FrontendBucketObjects"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
    ]
    resources = concat(
      ["${var.frontend_bucket_arn}/*", "${var.media_bucket_arn}/*"],
      [for arn in var.extra_frontend_bucket_arns : "${arn}/*"],
      [for arn in var.extra_media_bucket_arns : "${arn}/*"],
    )
  }

  statement {
    sid     = "CloudFrontInvalidate"
    effect  = "Allow"
    actions = ["cloudfront:CreateInvalidation"]
    # cloudfront_distribution_arn이 알려지면(02-runtime 배포 후 tfvars에 채워 넣으면)
    # 그 배포로만 좁힌다. 모르는 동안(최초 부트스트랩 등)도 최소한 이 계정으로는
    # 스코프를 좁혀서, 계정을 공유하는 다른 팀 CloudFront 배포까지 열리지 않게 한다.
    resources = var.cloudfront_distribution_arn != null ? [var.cloudfront_distribution_arn] : ["arn:aws:cloudfront::${data.aws_caller_identity.current.account_id}:distribution/*"]
  }
}

resource "aws_iam_role_policy" "github_actions" {
  name   = "github-actions-deploy"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.permissions.json
}

# terraform-apply.yml/terraform-destroy.yml 전용 role. 배포 role(위 github_actions)과
# 분리하는 이유는 terraform-apply.yml 안의 에러 메시지("배포 전용 AWS_ROLE_ARN을
# 대신 사용하면 안 됩니다")에 남아있다 - terraform apply/destroy는 이 프로젝트가
# 만드는 모든 리소스 타입에 걸쳐 훨씬 넓은 권한이 필요해서, 매 배포마다 도는 CD role에
# 그 권한을 얹으면 CD가 실수로 인프라를 바꿀 수 있는 범위가 커진다.
resource "aws_iam_role" "github_actions_terraform" {
  count              = var.create_terraform_role ? 1 : 0
  name               = "github-actions-lion-team3-${var.environment}-terraform"
  assume_role_policy = data.aws_iam_policy_document.trust.json
}

data "aws_iam_policy_document" "terraform_permissions" {
  count = var.create_terraform_role ? 1 : 0

  # 이 role 스스로 github_actions/github_actions_db_power 같은 lion-team3 계열
  # role/policy를 만들고 고칠 수 있어야 terraform apply가 이 모듈 자체를 관리할 수
  # 있다. 다른 팀 role까지 건드리지 못하게 이름으로 좁힌다.
  statement {
    sid    = "ScopedIam"
    effect = "Allow"
    actions = [
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:ListRolePolicies",
      "iam:ListAttachedRolePolicies",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:PassRole",
      "iam:ListInstanceProfilesForRole",
    ]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/lion-team3-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/github-actions-lion-team3-*",
    ]
  }

  statement {
    sid    = "IamReadOnly"
    effect = "Allow"
    actions = [
      "iam:ListRoles",
      "iam:ListOpenIDConnectProviders",
      "iam:GetOpenIDConnectProvider",
      "iam:ListPolicies",
      "iam:GetPolicy",
      "iam:GetPolicyVersion",
    ]
    resources = ["*"]
  }

  # 이 프로젝트가 실제로 만드는 서비스 타입으로만 좁힌다(계정을 공유하는 다른 팀의
  # 서비스까지는 안 건드림). 리소스 단위로 더 좁히는 건 EKS/EC2/RDS 등 대부분
  # 서비스가 apply 시점엔 아직 존재하지 않는 리소스라 비현실적이라 서비스 단위로 그침.
  statement {
    sid    = "ProjectServicesFull"
    effect = "Allow"
    actions = [
      "eks:*",
      "ec2:*",
      "elasticloadbalancing:*",
      "autoscaling:*",
      "rds:*",
      "elasticache:*",
      "sqs:*",
      "cognito-idp:*",
      "s3:*",
      "cloudfront:*",
      "acm:*",
      "wafv2:*",
      "route53:*",
      "ecr:*",
      "ssm:*",
      "logs:*",
      "kms:*",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_actions_terraform" {
  count  = var.create_terraform_role ? 1 : 0
  name   = "terraform-apply-destroy"
  role   = aws_iam_role.github_actions_terraform[0].id
  policy = data.aws_iam_policy_document.terraform_permissions[0].json
}

# db-power.yml(야간 dev Postgres EC2 stop/start) 전용 role. 인스턴스 하나만 건드릴
# 수 있게 terraform role보다도 훨씬 좁게 잡는다 - 이 role이 뚫려도 피해가 그 인스턴스
# 하나로 끝나게.
resource "aws_iam_role" "github_actions_db_power" {
  count              = var.create_db_power_role ? 1 : 0
  name               = "github-actions-lion-team3-db-power"
  assume_role_policy = data.aws_iam_policy_document.trust.json
}

data "aws_iam_policy_document" "db_power_permissions" {
  count = var.create_db_power_role ? 1 : 0

  statement {
    sid       = "StartStopOneInstance"
    effect    = "Allow"
    actions   = ["ec2:StartInstances", "ec2:StopInstances"]
    resources = ["arn:aws:ec2:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:instance/${var.db_power_instance_id}"]
  }

  # DescribeInstances는 리소스 수준 권한을 지원하지 않는다 - stop/start 성공 여부
  # 확인용으로만 쓰이므로 읽기 전용으로 계정 스코프 허용.
  statement {
    sid       = "DescribeReadOnly"
    effect    = "Allow"
    actions   = ["ec2:DescribeInstances", "ec2:DescribeInstanceStatus"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_actions_db_power" {
  count  = var.create_db_power_role ? 1 : 0
  name   = "db-start-stop"
  role   = aws_iam_role.github_actions_db_power[0].id
  policy = data.aws_iam_policy_document.db_power_permissions[0].json
}

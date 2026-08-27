data "aws_iam_policy_document" "trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(var.oidc_provider_url, "https://", "")}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(var.oidc_provider_url, "https://", "")}:sub"
      values   = ["system:serviceaccount:${var.namespace}:member-service"]
    }
  }
}

resource "aws_iam_role" "this" {
  name               = "lion-team3-${var.environment}-member-service"
  assume_role_policy = data.aws_iam_policy_document.trust.json
}

# CognitoAuthClient(backend/modules/member)가 실제로 호출하는 Admin API만 딱 허용한다.
# AdminCreateUser/AdminSetUserPassword = 회원가입, AdminDeleteUser = 가입 롤백,
# AdminInitiateAuth = 로그인/토큰 갱신(ADMIN_USER_PASSWORD_AUTH, REFRESH_TOKEN_AUTH).
data "aws_iam_policy_document" "permissions" {
  statement {
    sid    = "CognitoAdminAuth"
    effect = "Allow"
    actions = [
      "cognito-idp:AdminCreateUser",
      "cognito-idp:AdminSetUserPassword",
      "cognito-idp:AdminDeleteUser",
      "cognito-idp:AdminInitiateAuth",
    ]
    resources = [var.user_pool_arn]
  }
}

resource "aws_iam_role_policy" "this" {
  name   = "member-service-permissions"
  role   = aws_iam_role.this.id
  policy = data.aws_iam_policy_document.permissions.json
}

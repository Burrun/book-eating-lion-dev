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
      values   = ["system:serviceaccount:${var.namespace}:order-service"]
    }
  }
}

resource "aws_iam_role" "this" {
  name               = "lion-team3-${var.environment}-order-service"
  assume_role_policy = data.aws_iam_policy_document.trust.json
}

# SqsBookPurchasePublisher가 구매 확정 afterCommit 훅에서 이 큐로 발행한다
# (ai-service의 SqsPurchaseListener가 받아 ai_db.purchased_books에 적재).
# 예외를 삼키고 로그만 남기는 구조라, 권한이 없으면 결제는 성공한 채로 이벤트만
# 조용히 사라진다 - 증상은 RAG의 "구매한 책에서 근거를 찾지 못했습니다"로만 나온다.
data "aws_iam_policy_document" "permissions" {
  statement {
    sid       = "PurchaseChannelPublish"
    effect    = "Allow"
    actions   = ["sqs:SendMessage", "sqs:GetQueueAttributes"]
    resources = [var.purchase_channel_arn]
  }
}

resource "aws_iam_role_policy" "this" {
  name   = "order-service-permissions"
  role   = aws_iam_role.this.id
  policy = data.aws_iam_policy_document.permissions.json
}

locals {
  # S3 Vectors provider가 아직 없어 null일 수 있다 (01-data ai_pipeline 참고).
  # null을 걸러내고, 하나라도 남으면 그때만 S3 Vectors 권한 statement를 만든다.
  vector_index_arns = [
    for arn in [var.recommendation_index_arn, var.purchased_book_rag_index_arn] : arn if arn != null
  ]
}

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

    # ai-rag/ai-bot 두 Deployment가 이미지는 같지만 워크로드가 갈린다 (k8s-명세.md §1.4).
    # 둘 다 이 역할을 쓸 수 있게 서비스 계정 둘 다 허용한다.
    condition {
      test     = "StringEquals"
      variable = "${replace(var.oidc_provider_url, "https://", "")}:sub"
      values = [
        "system:serviceaccount:lion-app:ai-rag",
        "system:serviceaccount:lion-app:ai-bot",
      ]
    }
  }
}

resource "aws_iam_role" "this" {
  name               = "lion-team3-${var.environment}-ai-service"
  assume_role_policy = data.aws_iam_policy_document.trust.json
}

data "aws_iam_policy_document" "permissions" {
  statement {
    sid       = "BedrockInvoke"
    effect    = "Allow"
    actions   = ["bedrock:InvokeModel"]
    resources = var.bedrock_model_arns
  }

  statement {
    sid       = "IngestChannelConsume"
    effect    = "Allow"
    actions   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
    resources = [var.ingest_channel_arn]
  }

  statement {
    sid       = "PurchaseChannelConsume"
    effect    = "Allow"
    actions   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
    resources = [var.purchase_channel_arn]
  }

  dynamic "statement" {
    for_each = length(local.vector_index_arns) > 0 ? [1] : []
    content {
      sid    = "S3VectorsReadWrite"
      effect = "Allow"
      # GetIndex가 빠져 있으면 앱이 기동 시점에 하는 인덱스 검증(차원/거리척도/비필터키
      # 확인 - k8s/ai/configmap.yaml AI_VECTOR_INDEX 주석 참고)에서 AccessDenied로 죽는다
      # (2026-08-21 dev 실배포에서 실제로 겪음 - GetIndex 호출이 크래시 직전 마지막 로그).
      actions   = ["s3vectors:GetIndex", "s3vectors:GetVectors", "s3vectors:PutVectors", "s3vectors:QueryVectors"]
      resources = local.vector_index_arns
    }
  }
}

resource "aws_iam_role_policy" "this" {
  name   = "ai-service-permissions"
  role   = aws_iam_role.this.id
  policy = data.aws_iam_policy_document.permissions.json
}

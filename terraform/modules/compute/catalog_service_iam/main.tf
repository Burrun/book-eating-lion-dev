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
      values   = ["system:serviceaccount:${var.namespace}:catalog-service"]
    }
  }
}

resource "aws_iam_role" "this" {
  name               = "lion-team3-${var.environment}-catalog-service"
  assume_role_policy = data.aws_iam_policy_document.trust.json
}

# S3EbookStorageAdapter가 EPUB 열람/업로드 Presigned URL을 발급하는 데 쓴다
# (createReadUrl/createUploadUrl). S3Presigner는 서명 시점에 실제 자격증명이
# 있어야 해서, IRSA가 없으면 SdkClientException으로 죽는다 - ai/member와
# 같은 이유로 처음부터 빠져 있었다(2026-08-27 dev 실배포에서 실제로 겪음).
data "aws_iam_policy_document" "permissions" {
  statement {
    sid       = "EbookObjectReadWrite"
    effect    = "Allow"
    actions   = ["s3:GetObject", "s3:PutObject"]
    resources = ["${var.media_bucket_arn}/*"]
  }

  # SqsBookIngestPublisher가 신간 등록 afterCommit 훅에서 이 큐로 발행한다
  # (ai-service의 SqsIngestListener가 받아 EPUB을 벡터로 적재). 예외를 삼키고
  # 로그만 남기는 구조라 권한이 없으면 조용히 인제스트가 통째로 빠진다.
  statement {
    sid       = "IngestChannelPublish"
    effect    = "Allow"
    actions   = ["sqs:SendMessage", "sqs:GetQueueAttributes"]
    resources = [var.ingest_channel_arn]
  }
}

resource "aws_iam_role_policy" "this" {
  name   = "catalog-service-permissions"
  role   = aws_iam_role.this.id
  policy = data.aws_iam_policy_document.permissions.json
}

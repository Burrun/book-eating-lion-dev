# dev/prod 각 계층(00-base/01-data/02-runtime)의 backend.tf가 참조하는
# S3 버킷 + DynamoDB lock 테이블. 이름은 environments/{env}/*/backend.tf와
# 반드시 일치해야 한다 - 바꾸면 거기도 같이 바꿀 것.
#
# 2026-08-20에 이 버킷/테이블을 콘솔에서 실수로 지운 적이 있어서(인프라
# 정리 중 같이 삭제됨), 같은 사고를 막기 위해 prevent_destroy와 AWS 측
# deletion_protection을 걸어 둔다. 정말 다시 지워야 하면 이 lifecycle을
# 먼저 지우거나 deletion_protection을 false로 바꾸고 apply한 다음 destroy할 것.

locals {
  environments = ["dev", "prod"]
}

resource "aws_s3_bucket" "tfstate" {
  for_each = toset(local.environments)

  bucket = "book-eating-lion-tfstate-${each.key}"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "tfstate" {
  for_each = aws_s3_bucket.tfstate

  bucket = each.value.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  for_each = aws_s3_bucket.tfstate

  bucket = each.value.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "tfstate" {
  for_each = aws_s3_bucket.tfstate

  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_dynamodb_table" "tflock" {
  for_each = toset(local.environments)

  name         = "book-eating-lion-tflock-${each.key}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  deletion_protection_enabled = true

  lifecycle {
    prevent_destroy = true
  }
}

# 버킷 정책은 여기서 안 만든다. "CloudFront를 통해서만 접근 가능"하게 잠그는
# aws_s3_bucket_policy는 CloudFront 배포 ARN을 조건으로 걸어야 하는데, 그 배포는
# 02-runtime의 edge_routing에서만 존재한다 (TERRAFORM_STRUCTURE.md §3.1-5 참고).
# 그래서 지금은 퍼블릭 접근만 막아두고, OAC 기반 버킷 정책은 edge_routing이 붙인다.

resource "aws_s3_bucket" "frontend" {
  bucket = var.frontend_bucket_name
}

resource "aws_s3_bucket" "media" {
  bucket = var.media_bucket_name
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_public_access_block" "media" {
  bucket = aws_s3_bucket.media.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_versioning" "media" {
  bucket = aws_s3_bucket.media.id
  versioning_configuration {
    status = "Enabled"
  }
}

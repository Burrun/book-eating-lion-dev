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

resource "aws_s3_bucket_server_side_encryption_configuration" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "media" {
  bucket = aws_s3_bucket.media.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# EbookAccess가 발급하는 presigned GET URL을 브라우저가 fetch()로 직접 읽으려면
# 필요하다. GET은 "simple request"라 프리플라이트(OPTIONS) 없이 바로 나가지만,
# 응답에 Access-Control-Allow-Origin이 없으면 200을 받고도 브라우저가 스크립트에
# 본문을 못 넘긴다 - 지금까지 이 리소스 자체가 없어 겪은 문제다.
resource "aws_s3_bucket_cors_configuration" "media" {
  bucket = aws_s3_bucket.media.id

  cors_rule {
    allowed_methods = ["GET"]
    allowed_origins = var.media_cors_allowed_origins
    # epub.js(react-reader)가 Range 요청으로 청크 단위 로드를 시도할 수 있다 - Range는
    # CORS-safelisted 헤더가 아니라 허용 안 하면 프리플라이트에서 막힌다.
    allowed_headers = ["Range"]
    expose_headers  = ["Content-Length", "Content-Range", "Accept-Ranges", "ETag"]
    max_age_seconds = 3000
  }

  # 관리자는 브라우저에서 S3 Presigned URL로 EPUB 원본을 직접 업로드한다.
  # 조회 규칙과 분리해 업로드에 필요한 최소 메서드/헤더만 허용한다.
  cors_rule {
    allowed_methods = ["PUT"]
    allowed_origins = var.media_cors_allowed_origins
    allowed_headers = ["Content-Type"]
    max_age_seconds = 3000
  }
}

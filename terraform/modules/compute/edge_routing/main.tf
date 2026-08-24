# 트래픽 경로: 도메인 -> CloudFront -> (기본) S3 프론트엔드 / (/api/*) ALB(ingress-nginx NLB)
# 도메인이 ALB를 직접 가리키는 레코드는 만들지 않는다 (TERRAFORM_STRUCTURE.md §3.3-4).

# /api/* ordered_cache_behavior가 쓰는 AWS 관리형 정책 - Host 헤더를 포함해 뷰어
# 요청을 오리진(ALB)에 그대로 전달하면서 캐싱은 끈다. 아래 forwarded_values 관련
# 주석 참고.
data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_origin_request_policy" "all_viewer" {
  name = "Managed-AllViewer"
}

# /covers/* 캐싱용 - S3 정적 파일이라 쿼리스트링/쿠키 없이 URL만으로 캐시 키를 잡는다.
data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "lion-team3-${var.environment}-frontend-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# 도서 표지 등 정적 미디어 전용 오리진. frontend와 분리하는 이유는 이 파일들이
# 프론트엔드 배포(dist/) 주기와 무관하게 독립적으로 갱신되기 때문이다 - 표지 하나
# 바꾸겠다고 SPA 전체를 재배포할 이유가 없다.
resource "aws_cloudfront_origin_access_control" "media" {
  name                              = "lion-team3-${var.environment}-media-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# React Router 같은 클라이언트 사이드 라우팅용 - 확장자 없는 경로(예: /mypage)를
# index.html로 돌려보내 React Router가 처리하게 한다.
#
# custom_error_response(배포 전체에 걸림, 오리진 무관하게 403/404를 전부 잡음)를 쓰면
# /api/* 에서 나는 진짜 404도 잡아채서 index.html을 돌려주게 된다 - 프론트가 API
# 에러를 못 받는 실제 버그였다. 그래서 default_cache_behavior(S3 오리진)에만 붙는
# CloudFront Function으로 범위를 좁혔다. /api/* ordered_cache_behavior엔 이 함수를
# 안 붙이므로 API 오리진은 영향 없다.
resource "aws_cloudfront_function" "spa_routing" {
  name    = "lion-team3-${var.environment}-spa-routing"
  runtime = "cloudfront-js-2.0"
  publish = true
  code    = <<-EOT
    function handler(event) {
      var request = event.request;
      var uri = request.uri;
      if (!uri.includes('.')) {
        request.uri = '/index.html';
      }
      return request;
    }
  EOT
}

resource "aws_cloudfront_distribution" "this" {
  enabled             = true
  aliases             = [var.domain_name, "www.${var.domain_name}"]
  price_class         = "PriceClass_200" # 한국/아시아 위주 - 북미/유럽 엣지까지는 불필요
  default_root_object = "index.html"

  origin {
    domain_name              = var.frontend_bucket_domain_name
    origin_id                = "s3-frontend"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  origin {
    domain_name              = var.media_bucket_domain_name
    origin_id                = "s3-media"
    origin_access_control_id = aws_cloudfront_origin_access_control.media.id
  }

  origin {
    domain_name = var.alb_dns_name
    origin_id   = "alb-api"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only" # ingress-nginx는 내부 TLS 미종단, CloudFront가 공개 HTTPS를 종단
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "s3-frontend"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_routing.arn
    }
  }

  ordered_cache_behavior {
    path_pattern           = "/api/*"
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "alb-api"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    # 레거시 forwarded_values는 커스텀 오리진(ALB)에 원본 Host 헤더를 절대 전달하지
    # 못한다(headers 목록에 "Host"를 넣어도 무시되고 오리진 자체 도메인명으로 고정됨).
    # ingress-nginx의 lion-ingress가 host: dev.ajttk.com 기준으로 라우팅하기 때문에,
    # Host가 안 넘어가면 CloudFront를 거치는 /api/* 요청이 전부 nginx 기본 404로
    # 떨어진다(2026-08-23 dev 실배포에서 실제로 겪음 - 회원가입/로그인 전부 404).
    # AWS 관리형 Origin Request Policy "AllViewer"는 Host를 포함한 모든 뷰어 헤더/
    # 쿠키/쿼리스트링을 오리진에 그대로 전달한다 - 캐싱은 API라 여전히 끈다("CachingDisabled").
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
  }

  # /api/*에는 안 걸리는 별도 경로 - 없으면 default_cache_behavior(S3 오리진)로 떨어져서
  # WebSocket 핸드셰이크가 index.html(HTTP 200, text/html)을 받고 그대로 끝나버린다
  # (2026-08-23 dev 실배포에서 실제로 겪음 - AI 챗봇이 계속 안 됨). CloudFront는 GET +
  # Upgrade/Connection 헤더가 오리진까지 그대로 전달되면 WebSocket을 별도 설정 없이도
  # 프록시한다 - AllViewer가 그 헤더들을 포함해 전부 전달한다.
  ordered_cache_behavior {
    path_pattern           = "/ws/ai/chat"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "alb-api"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
  }

  # 도서 표지 등 정적 미디어. S3 오리진(s3-media)이라 GET/HEAD만 허용하고
  # AWS 관리형 CachingOptimized로 엣지에 오래 캐싱한다 - PUT/POST는 CI/CD가
  # IAM으로 직접 S3에 올리지 CloudFront를 거치지 않는다.
  ordered_cache_behavior {
    path_pattern           = "/covers/*"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "s3-media"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    cache_policy_id = data.aws_cloudfront_cache_policy.caching_optimized.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = var.acm_certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  web_acl_id = var.waf_web_acl_arn
}

# S3 버킷은 이 CloudFront 배포에서만 접근 가능하도록 제한 (버킷 자체는 00-base storage 소유).
data "aws_iam_policy_document" "frontend_bucket" {
  statement {
    sid       = "AllowCloudFrontOAC"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${var.frontend_bucket_arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.this.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "frontend" {
  bucket = var.frontend_bucket_id
  policy = data.aws_iam_policy_document.frontend_bucket.json
}

# media 버킷도 이 CloudFront 배포에서만 접근 가능하도록 제한 (버킷 자체는 00-base storage 소유).
data "aws_iam_policy_document" "media_bucket" {
  statement {
    sid       = "AllowCloudFrontOAC"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${var.media_bucket_arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.this.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "media" {
  bucket = var.media_bucket_id
  policy = data.aws_iam_policy_document.media_bucket.json
}

resource "aws_route53_record" "apex" {
  zone_id = var.route53_zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.this.domain_name
    zone_id                = aws_cloudfront_distribution.this.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "www" {
  zone_id = var.route53_zone_id
  name    = "www.${var.domain_name}"
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.this.domain_name
    zone_id                = aws_cloudfront_distribution.this.hosted_zone_id
    evaluate_target_health = false
  }
}

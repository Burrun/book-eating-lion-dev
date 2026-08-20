# 트래픽 경로: 도메인 -> CloudFront -> (기본) S3 프론트엔드 / (/api/*) ALB(ingress-nginx NLB)
# 도메인이 ALB를 직접 가리키는 레코드는 만들지 않는다 (TERRAFORM_STRUCTURE.md §3.3-4).

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "lion-team3-${var.environment}-frontend-oac"
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

    # API는 캐싱하지 않고 요청을 그대로 전달한다.
    forwarded_values {
      query_string = true
      headers      = ["Authorization", "Content-Type", "X-Member-Id"]
      cookies {
        forward = "all"
      }
    }

    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
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

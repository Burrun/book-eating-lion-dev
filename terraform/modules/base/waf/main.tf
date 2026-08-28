# WebACL 정의만 여기서 만든다. CloudFront에 실제로 붙이는 건
# 02-runtime의 edge_routing이 aws_cloudfront_distribution.web_acl_id에
# 이 ARN을 넣는 방식으로 한다 (TERRAFORM_STRUCTURE.md §3.1-4 참고).
resource "aws_wafv2_ip_set" "allowlist" {
  count              = length(var.ip_allowlist) > 0 ? 1 : 0
  name               = "${var.name}-ip-allowlist"
  scope              = "CLOUDFRONT"
  ip_address_version = "IPV4"
  addresses          = var.ip_allowlist
}

resource "aws_wafv2_web_acl" "this" {
  name  = var.name
  scope = "CLOUDFRONT"

  default_action {
    allow {}
  }

  dynamic "rule" {
    for_each = length(var.ip_allowlist) > 0 ? [1] : []
    content {
      name     = "ip-allowlist"
      priority = 0

      action {
        allow {}
      }

      statement {
        ip_set_reference_statement {
          arn = aws_wafv2_ip_set.allowlist[0].arn
        }
      }

      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = "${var.name}-ip-allowlist"
        sampled_requests_enabled   = true
      }
    }
  }

  # 기획서 근거: 경쟁사 도서 스크래핑 방어, 악성 봇 차단
  rule {
    name     = "rate-limit"
    priority = length(var.ip_allowlist) > 0 ? 1 : 0

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = var.rate_limit
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name}-rate-limit"
      sampled_requests_enabled   = true
    }
  }

  # SQLi 등 일반 공격 패턴 방어
  rule {
    name     = "aws-managed-common"
    priority = length(var.ip_allowlist) > 0 ? 2 : 1

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name}-aws-managed-common"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = var.name
    sampled_requests_enabled   = true
  }
}

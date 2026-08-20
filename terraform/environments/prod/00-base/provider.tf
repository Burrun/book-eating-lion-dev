provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "lion"
      Team        = "Team3"
      Owner       = "book-eating-lion-team3"
      Environment = var.environment
      ManagedBy   = "terraform"
      Layer       = "base"
    }
  }
}

# 00-base 전용: CloudFront가 요구하는 acm_cert/waf만 이 alias로 생성
# (CloudFront는 us-east-1에서 발급된 ACM 인증서와 CLOUDFRONT 스코프 WAF만 받는다).
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = {
      Project     = "lion"
      Team        = "Team3"
      Owner       = "book-eating-lion-team3"
      Environment = var.environment
      ManagedBy   = "terraform"
      Layer       = "base"
    }
  }
}

# CloudFront는 us-east-1에서 발급된 ACM 인증서만 받는다 (AWS 고정 제약).
# 이 모듈은 반드시 us-east-1 provider alias로 호출해야 한다:
#   module "acm_cert" {
#     providers = { aws = aws.us_east_1 }
#     ...
#   }
terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      configuration_aliases = [aws]
    }
  }
}

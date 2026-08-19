# CLOUDFRONT 스코프 WebACL은 us-east-1에서만 생성 가능하다 (acm_cert와 같은 이유).
# 반드시 us-east-1 provider alias로 호출해야 한다.
terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      configuration_aliases = [aws]
    }
  }
}

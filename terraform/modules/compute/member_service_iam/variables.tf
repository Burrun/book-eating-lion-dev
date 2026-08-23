variable "environment" {
  type = string
}

variable "oidc_provider_arn" {
  type = string
}

variable "oidc_provider_url" {
  description = "https:// 접두사가 붙은 전체 issuer URL"
  type        = string
}

variable "user_pool_arn" {
  description = "01-data auth 모듈이 만드는 Cognito User Pool ARN — Admin API 권한을 이 하나로 스코프한다"
  type        = string
}

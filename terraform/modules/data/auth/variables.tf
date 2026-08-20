variable "user_pool_name" {
  type = string
}

variable "custom_domain_name" {
  description = "Cognito Hosted UI 도메인 접두사 (예: book-eating-lion-prod → book-eating-lion-prod.auth.ap-northeast-2.amazoncognito.com)"
  type        = string
}

variable "callback_urls" {
  description = "OAuth 콜백 URL 목록 (프론트엔드 로그인 완료 후 리다이렉트)"
  type        = list(string)
}

variable "logout_urls" {
  type = list(string)
}

variable "domain_name" {
  description = "이 프로젝트가 소유할 도메인 (예: dev.ajttk.com)"
  type        = string
}

variable "parent_zone_domain" {
  description = "domain_name이 서브도메인일 때 그 부모 도메인 (이미 이 계정 Route53에 등록돼 있어야 함). null이면 NS 위임 레코드를 만들지 않는다"
  type        = string
  default     = null
}

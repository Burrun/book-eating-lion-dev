variable "environment" {
  type = string
}

# key = sync-github-config.sh / GitHub Secrets 접두사(소문자), value = 실제 DB 롤 이름.
# 앱의 application-prod.yml이 currentSchema=<key>_db 로 붙으므로 key는 스키마 이름과
# 짝이 맞아야 한다(catalog -> catalog_db 스키마 / catalog_svc 롤).
variable "accounts" {
  description = "서비스 키 -> DB 롤 이름"
  type        = map(string)

  default = {
    catalog = "catalog_svc"
    order   = "order_svc"
    member  = "member_svc"
    ai      = "ai_svc"
  }
}

variable "recovery_window_in_days" {
  description = "0이면 즉시 삭제. 이관 중 재생성이 잦아 기본을 0으로 둔다"
  type        = number
  default     = 0
}

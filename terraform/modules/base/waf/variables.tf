variable "name" {
  description = "WebACL 이름"
  type        = string
}

variable "rate_limit" {
  description = "5분 윈도우 기준 IP당 허용 요청 수 (경쟁사 스크래핑/봇 방어)"
  type        = number
  default     = 2000
}

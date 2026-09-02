variable "name" {
  description = "SNS Topic 이름"
  type        = string
}

variable "alert_email" {
  description = "장애/이상 알림을 받을 운영자 이메일"
  type        = string
}

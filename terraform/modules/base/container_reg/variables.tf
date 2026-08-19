variable "service_names" {
  description = "ECR 레포지토리를 만들 마이크로서비스 이름 목록 (기획서 기준: catalog, order, member, ai)"
  type        = list(string)
}

variable "image_tag_keep_count" {
  description = "레포지토리당 유지할 최근 태그 수"
  type        = number
  default     = 30
}

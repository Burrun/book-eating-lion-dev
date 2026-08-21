variable "environment" {
  description = "환경 이름 (dev | prod) - cidr_plans에서 CIDR 플랜을 조회하는 키로 씀"
  type        = string

  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment는 dev 또는 prod여야 합니다 (network_plan의 cidr_plans에 정의된 값만 지원)."
  }
}

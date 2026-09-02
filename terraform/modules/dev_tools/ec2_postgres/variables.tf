variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "app_subnet_id" {
  description = "인스턴스를 배치할 서브넷. data_subnet이 아니라 app_subnet을 쓴다 - data subnet은 NAT가 없어 패키지 설치가 안 됨"
  type        = string
}

variable "app_security_group_id" {
  description = "이 SG를 가진 것만 5432로 접속 허용 (aurora_pg와 동일한 접근 통제 원칙)"
  type        = string
}

variable "instance_type" {
  type    = string
  default = "t4g.micro"
}

variable "root_volume_size_gb" {
  description = "AL2023 arm64 AMI의 스냅샷이 요구하는 최소 크기 이상이어야 함 - 20GB로는 부족해서 apply가 실패했었다(2026-08-20, AWS가 시간이 지나며 AMI 기본 이미지 크기를 키워옴). AMI가 더 커지면 이 값도 같이 올려야 할 수 있음"
  type        = number
  default     = 30
}

variable "database_name" {
  type    = string
  default = "bookdb"

  validation {
    condition     = can(regex("^[a-z][a-z0-9_]{0,62}$", var.database_name))
    error_message = "database_name must be a valid unquoted PostgreSQL identifier."
  }
}

variable "master_username" {
  type    = string
  default = "bookadmin"
}

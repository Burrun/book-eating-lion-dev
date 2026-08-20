variable "environment" {
  description = "환경 이름 (dev | prod) - 태깅용"
  type        = string
}

variable "vpc_cidr" {
  description = "VPC 전체 CIDR 블록"
  type        = string
}

variable "availability_zones" {
  description = "사용할 가용 영역 목록 (2개, ap-northeast-2a/2c)"
  type        = list(string)

  validation {
    condition     = length(var.availability_zones) == 2
    error_message = "이 VPC 모듈은 2AZ 설계를 전제로 합니다. availability_zones는 정확히 2개여야 합니다."
  }
}

variable "public_subnet_cidrs" {
  description = "Public 서브넷 CIDR 목록 (availability_zones와 같은 순서, 2개)"
  type        = list(string)
}

variable "app_subnet_cidrs" {
  description = "Private App 서브넷 CIDR 목록 (EKS 노드용, 2개)"
  type        = list(string)
}

variable "data_subnet_cidrs" {
  description = "Private Data 서브넷 CIDR 목록 (Aurora/Valkey용, 2개)"
  type        = list(string)
}

variable "single_nat_gateway" {
  description = "true면 NAT Gateway/EIP를 1개(첫 AZ)만 만들어 두 AZ가 공유한다 - AZ 장애 격리를 포기하는 대신 비용을 절반으로 줄인다(NAT Gateway는 시간당 고정 과금). 비용에 민감한 dev 등에서만 true로 켤 것, prod는 기본값(false, AZ별 NAT) 유지"
  type        = bool
  default     = false
}

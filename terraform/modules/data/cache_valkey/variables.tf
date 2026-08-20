variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "data_subnet_ids" {
  type = list(string)
}

variable "app_security_group_id" {
  type = string
}

variable "engine_version" {
  description = "Valkey 엔진 버전 - 업그레이드/롤백 시 코드 수정 없이 이 값만 바꾸도록 변수로 분리"
  type        = string
  default     = "8.2"
}

variable "node_type" {
  type    = string
  default = "cache.t4g.medium"
}

variable "sns_topic_arn" {
  type = string
}

variable "replica_count" {
  description = "Primary 외 Replica 개수. 기본 1(2AZ). 2로 올리려면 3번째 AZ 서브넷 필요"
  type        = number
  default     = 1
}

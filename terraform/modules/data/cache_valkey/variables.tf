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

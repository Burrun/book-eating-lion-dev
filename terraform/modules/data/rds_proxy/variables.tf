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

variable "cluster_security_group_id" {
  description = "aurora_pg 모듈 출력 - Proxy SG에서 이 SG로 5432 인바운드를 열어줘야 함"
  type        = string
}

variable "aurora_cluster_identifier" {
  type = string
}

variable "secrets_manager_arn" {
  type = string
}

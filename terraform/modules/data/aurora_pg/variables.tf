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
  description = "01-data 보안그룹들이 인바운드 소스로 참조하는 EKS 노드/Pod 공용 SG (00-base vpc 출력)"
  type        = string
}

variable "database_name" {
  type    = string
  default = "bookdb"
}

variable "master_username" {
  type    = string
  default = "bookadmin"
}

variable "sns_topic_arn" {
  description = "CloudWatch 알람 액션 대상 (00-base alerting 출력)"
  type        = string
}

variable "reader_count" {
  description = "Writer 외 Reader 인스턴스 개수. 0=초기단계, 1=기본값(2AZ), 2=강한 Multi-AZ 시연(3번째 AZ 서브넷 필요)"
  type        = number
  default     = 1
}

variable "min_capacity" {
  description = "Serverless v2 최소 ACU"
  type        = number
  default     = 0.5
}

variable "max_capacity" {
  description = "Serverless v2 최대 ACU"
  type        = number
  default     = 8
}

variable "deletion_protection" {
  type    = bool
  default = true
}

variable "skip_final_snapshot" {
  type    = bool
  default = false
}

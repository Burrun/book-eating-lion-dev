variable "environment" {
  type    = string
  default = "prod"
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "cluster_version" {
  type    = string
  default = "1.34" # 1.30은 지원 종료(2026-08-20 확인)
}

variable "domain_name" {
  type = string
}

variable "bedrock_model_arns" {
  type = list(string)
}

variable "admin_principal_arns" {
  description = "kubectl/terraform으로 이 클러스터를 관리할 사람(들)의 IAM 사용자/역할 ARN 목록 (eks_cluster 모듈로 그대로 전달)"
  type        = list(string)
  default     = []
}

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

# S3 Vectors는 Terraform provider 미지원이라 AWS CLI로 수동 생성 후 ARN을
# 여기 값으로 채운다 (인프라구성명세.md §7.5). 아직 안 만들었으면 null로 둔다.
variable "recommendation_index_arn" {
  type    = string
  default = null
}

variable "purchased_book_rag_index_arn" {
  type    = string
  default = null
}

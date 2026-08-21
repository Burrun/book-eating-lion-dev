variable "environment" {
  type    = string
  default = "sandbox"
}

# dev/02-runtime과의 차이점 — 이 레이어가 만드는 리소스(EKS/Karpenter/ALB/AI IRSA)의
# 이름·태그는 var.environment("sandbox")를 쓰지만, VPC/서브넷/SG/DB/Redis/SQS 같은
# 기반 리소스는 이 값(dev)의 00-base·01-data가 SSM에 publish한 걸 그대로 읽어서
# 재사용한다 — 02-runtime의 destroy+apply 반복 연습을 위해 만든 것이라, 매번 비용이
# 크고 오래 걸리는 VPC/DB까지 새로 만들 필요는 없다.
variable "base_environment" {
  type    = string
  default = "dev"
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "cluster_version" {
  type    = string
  default = "1.34"
}

variable "bedrock_model_arns" {
  type = list(string)
}

variable "admin_principal_arns" {
  description = "kubectl/terraform으로 이 클러스터를 관리할 사람(들)의 IAM 사용자/역할 ARN 목록 (eks_cluster 모듈로 그대로 전달)"
  type        = list(string)
  default     = []
}

# S3 Vectors는 Terraform provider 미지원이라 AWS CLI로 수동 생성한 값을 넣는다
# (인프라구성명세.md §7.5) — dev가 이미 만든 버킷/인덱스를 그대로 재사용한다.
variable "recommendation_index_arn" {
  type    = string
  default = null
}

variable "purchased_book_rag_index_arn" {
  type    = string
  default = null
}

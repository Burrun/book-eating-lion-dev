variable "environment" {
  type = string
}

variable "cluster_name" {
  type = string
}

variable "oidc_provider_arn" {
  type = string
}

variable "oidc_provider_url" {
  description = "https:// 접두사가 붙은 전체 issuer URL"
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  description = "NLB가 뜰 Public Subnet - AWS Load Balancer Controller가 이 서브넷에 kubernetes.io/role/elb 태그를 보고 자동 선택함"
  type        = list(string)
}

variable "aws_region" {
  type = string
}

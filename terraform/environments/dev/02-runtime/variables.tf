variable "environment" {
  type    = string
  default = "dev"
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

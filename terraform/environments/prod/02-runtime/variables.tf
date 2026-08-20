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
  default = "1.30"
}

variable "domain_name" {
  type = string
}

variable "bedrock_model_arns" {
  type = list(string)
}

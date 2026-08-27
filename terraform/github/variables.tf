variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "github_owner" {
  type    = string
  default = "Burrun"
}

variable "github_repository" {
  type    = string
  default = "book-eating-lion-dev"
}

variable "split_prod_aws_role_arn" {
  description = "split prod 00-base가 실제 생성된 뒤 전달한다. null이면 기존 prod AWS_ROLE_ARN을 건드리지 않는다."
  type        = string
  default     = null
}

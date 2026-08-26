variable "environment" {
  type    = string
  default = "integrated"
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

# ── DB: 새 인스턴스를 안 만든다 (main.tf 참고) ────────────────────
variable "database_name" {
  description = "dev EC2 Postgres 인스턴스 안에 새로 만들 논리 DB 이름"
  type        = string
  default     = "bookdb_prod"
}

variable "master_username" {
  description = "dev EC2 인스턴스에 이미 존재하는 마스터 계정과 반드시 같아야 한다 (새로 만드는 게 아니라 기존 계정으로 createdb만 함)"
  type        = string
  default     = "bookadmin"
}

# ── Valkey (prod 전용 - 새로 만듦, dev와 공유 안 함) ────────────────
variable "valkey_node_type" {
  type    = string
  default = "cache.t4g.medium"
}

variable "valkey_replica_count" {
  type    = number
  default = 1
}

# ── Cognito (prod 전용 User Pool - 새로 만듦) ───────────────────────
variable "user_pool_name" {
  type = string
}

variable "cognito_domain_prefix" {
  type = string
}

variable "cognito_callback_urls" {
  type = list(string)
}

variable "cognito_logout_urls" {
  type = list(string)
}

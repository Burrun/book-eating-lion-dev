variable "environment" {
  type    = string
  default = "prod"
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

# ── Aurora ───────────────────────────────────────────────────────
variable "database_name" {
  type    = string
  default = "bookdb_prod"
}

variable "master_username" {
  type    = string
  default = "bookadmin"
}

variable "reader_count" {
  type    = number
  default = 1
}

variable "aurora_deletion_protection" {
  type    = bool
  default = true
}

variable "aurora_skip_final_snapshot" {
  type    = bool
  default = false
}

variable "aurora_backup_retention_period" {
  description = "자동 백업(PITR) 보존 기간(일). prod는 실 주문/구매 데이터가 쌓이므로 AWS 기본값(1일)보다 넉넉하게 잡는다."
  type        = number
  default     = 7
}

# ── Valkey ───────────────────────────────────────────────────────
variable "valkey_node_type" {
  type    = string
  default = "cache.t4g.medium"
}

variable "valkey_replica_count" {
  type    = number
  default = 1
}

# ── Cognito ──────────────────────────────────────────────────────
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

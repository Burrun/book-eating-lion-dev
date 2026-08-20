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
  default = "bookdb"
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

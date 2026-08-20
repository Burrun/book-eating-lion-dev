variable "environment" {
  type    = string
  default = "dev"
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

# ── EC2 PostgreSQL (aurora_pg 대신, §6.3) ───────────────────────
variable "database_name" {
  type    = string
  default = "bookdb"
}

variable "master_username" {
  type    = string
  default = "bookadmin"
}

variable "ec2_postgres_instance_type" {
  type    = string
  default = "t4g.micro"
}

# ── Valkey ───────────────────────────────────────────────────────
variable "valkey_node_type" {
  type    = string
  default = "cache.t4g.medium"
}

variable "valkey_replica_count" {
  description = "dev는 비용 절감을 위해 기본 0(Replica 없음)"
  type        = number
  default     = 0
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

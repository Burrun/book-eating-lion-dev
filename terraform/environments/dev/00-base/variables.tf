variable "environment" {
  type    = string
  default = "dev"
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

# ── VPC ──────────────────────────────────────────────────────────
variable "vpc_cidr" {
  type = string
}

variable "availability_zones" {
  type = list(string)
}

variable "public_subnet_cidrs" {
  type = list(string)
}

variable "app_subnet_cidrs" {
  type = list(string)
}

variable "data_subnet_cidrs" {
  type = list(string)
}

# ── DNS / ACM / WAF ──────────────────────────────────────────────
variable "domain_name" {
  type = string
}

variable "waf_rate_limit" {
  type    = number
  default = 2000
}

# ── S3 ───────────────────────────────────────────────────────────
variable "frontend_bucket_name" {
  type = string
}

variable "media_bucket_name" {
  type = string
}

# ── ECR ──────────────────────────────────────────────────────────
variable "service_names" {
  description = "기획서 '3. 도메인' 기준 4개 확정 (문의채팅은 ai 도메인 하위 기능이라 별도 레포 아님)"
  type        = list(string)
  default     = ["catalog", "order", "member", "ai"]
}

# ── Alerting ─────────────────────────────────────────────────────
variable "alert_email" {
  type = string
}

# ── GitHub OIDC ──────────────────────────────────────────────────
variable "github_org" {
  type = string
}

variable "github_repo" {
  type = string
}

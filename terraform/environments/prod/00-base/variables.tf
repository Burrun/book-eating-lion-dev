variable "environment" {
  type    = string
  default = "prod"
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

# ── VPC ──────────────────────────────────────────────────────────
# vpc_cidr/availability_zones/subnet_cidrs는 modules/base/network_plan에서
# environment로 조회해서 쓴다(main.tf 참고) - 여기서 변수로 받지 않는다.

variable "single_nat_gateway" {
  description = "true면 NAT Gateway 1개로 두 AZ가 공유 (AZ 장애 격리 포기, 비용 절반). prod는 기본값(false)을 유지할 것 - 격리가 비용보다 중요"
  type        = bool
  default     = false
}

# ── DNS / ACM / WAF ──────────────────────────────────────────────
variable "domain_name" {
  type = string
}

variable "parent_zone_domain" {
  description = "domain_name이 서브도메인일 때 그 부모 도메인 (이미 이 계정 Route53에 등록돼 있어야 함) - NS 위임 레코드를 자동으로 추가하는 데 씀"
  type        = string
}

variable "waf_rate_limit" {
  type    = number
  default = 2000
}

variable "waf_ip_allowlist" {
  description = "WAF를 우회할 고정 IPv4 CIDR 목록"
  type        = list(string)
  default     = []
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

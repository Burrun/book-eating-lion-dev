variable "environment" {
  description = "이 계층 전체의 네이밍/태깅 기준. dev/prod와 나란한 세 번째 값 - VPC는 새로 안 만들지만(main.tf 참고) ECR/OIDC/도메인/버킷은 이 이름으로 독립된다"
  type        = string
  default     = "integrated"
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

# ── DNS / ACM / WAF ──────────────────────────────────────────────
# dev.ajttk.com은 이미 dev/00-base가 소유 - 여기서는 건드리지 않는다.
# integrated가 새로 서비스하는 건 prod 도메인 하나뿐이다.
variable "domain_name" {
  description = "이 환경이 새로 서비스할 도메인 (prod 도메인, 예: book.ajttk.com). dev.ajttk.com은 여기서 다루지 않음 - 아래 파일 상단 주석 참고"
  type        = string
}

variable "parent_zone_domain" {
  description = "domain_name의 부모 도메인 (이미 계정 Route53에 있음) - NS 위임용"
  type        = string
}

variable "waf_rate_limit" {
  type    = number
  default = 2000
}

# ── S3 (prod 프론트엔드/미디어 - dev 것과 분리, 새로 필요) ─────────
variable "frontend_bucket_name" {
  type = string
}

variable "media_bucket_name" {
  type = string
}

# ── ECR ──────────────────────────────────────────────────────────
# dev의 ECR을 재사용하지 않고 새로 만든다 - container_reg 모듈 자체가
# "environment별로 분리해서 이름 충돌을 막는다"는 설계이고(모듈 주석 참고),
# CI가 dev/prod 이미지를 같은 레포에 태그만 다르게 넣는 건 오히려 실수로
# 잘못된 태그를 잘못된 네임스페이스에 배포할 위험을 키운다.
variable "service_names" {
  type    = list(string)
  default = ["catalog", "order", "member", "ai"]
}

# ── GitHub OIDC ──────────────────────────────────────────────────
variable "github_org" {
  type = string
}

variable "github_repo" {
  type = string
}

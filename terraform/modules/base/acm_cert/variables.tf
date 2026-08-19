variable "domain_name" {
  description = "인증서를 발급할 도메인. 와일드카드(*.domain)도 함께 커버하려면 SAN으로 추가한다"
  type        = string
}

variable "route53_zone_id" {
  description = "DNS 검증 레코드를 심을 Hosted Zone ID (같은 00-base 내 dns_zone 모듈 출력)"
  type        = string
}

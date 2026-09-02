variable "environment" {
  type = string
}

variable "domain_name" {
  type = string
}

variable "alb_dns_name" {
  description = "ingress_alb 출력 (실제로는 NLB 호스트명)"
  type        = string
}

variable "route53_zone_id" {
  type = string
}

variable "acm_certificate_arn" {
  description = "us-east-1 인증서 (00-base acm_cert 출력)"
  type        = string
}

variable "waf_web_acl_arn" {
  type = string
}

variable "frontend_bucket_id" {
  type = string
}

variable "frontend_bucket_arn" {
  type = string
}

variable "frontend_bucket_domain_name" {
  type = string
}

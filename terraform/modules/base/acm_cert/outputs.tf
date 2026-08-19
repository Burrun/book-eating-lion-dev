output "acm_certificate_arn" {
  description = "CloudFront 전용 (us-east-1) 검증 완료 인증서 ARN"
  value       = aws_acm_certificate_validation.this.certificate_arn
}

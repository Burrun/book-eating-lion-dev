output "vpc_id" {
  description = "dev와 동일한 값 (재사용, 새로 만들지 않음)"
  value       = data.aws_ssm_parameter.dev_vpc_id.value
  sensitive   = true # aws_ssm_parameter data source 값은 terraform이 자동으로 sensitive 취급함
}

output "route53_name_servers" {
  description = "book.ajttk.com을 위해 ajttk.com(부모 zone)에 이미 NS 위임까지 자동으로 들어간다 - 보통 추가 조치 불필요. 확인용."
  value       = module.dns_zone.route53_name_servers
}

output "ecr_repository_urls" {
  value = module.container_reg.repository_urls
}

output "github_actions_role_arn" {
  value = module.github_oidc.github_actions_role_arn
}

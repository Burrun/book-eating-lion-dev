output "vpc_id" {
  value = module.vpc.vpc_id
}

output "app_security_group_id" {
  value = module.vpc.app_security_group_id
}

output "route53_name_servers" {
  description = "도메인 등록기관(가비아 등)에 네임서버로 등록해야 하는 값"
  value       = module.dns_zone.route53_name_servers
}

output "ecr_repository_urls" {
  value = module.container_reg.repository_urls
}

output "github_actions_role_arn" {
  value = module.github_oidc.github_actions_role_arn
}

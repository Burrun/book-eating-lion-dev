output "secret_arns" {
  description = "RDS Proxy의 additional_auth_secret_arns에 그대로 넘긴다"
  value       = [for k in keys(var.accounts) : aws_secretsmanager_secret.this[k].arn]
}

output "secret_arn_by_account" {
  description = "서비스 키 -> 시크릿 ARN. SSM에 기록해 sync-github-config.sh가 조회한다"
  value       = { for k, _ in var.accounts : k => aws_secretsmanager_secret.this[k].arn }
}

output "usernames" {
  description = "서비스 키 -> DB 롤 이름"
  value       = var.accounts
}

output "passwords" {
  description = "서비스 키 -> 생성된 비밀번호. 00-init.sql의 CREATE ROLE에 넣을 값"
  value       = { for k, _ in var.accounts : k => random_password.this[k].result }
  sensitive   = true
}

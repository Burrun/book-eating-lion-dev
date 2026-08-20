output "repository_urls" {
  value = { for name, repo in aws_ecr_repository.this : name => repo.repository_url }
}

output "repository_arns" {
  description = "github_oidc 모듈의 IAM 정책 resource 절에 씀"
  value       = { for name, repo in aws_ecr_repository.this : name => repo.arn }
}

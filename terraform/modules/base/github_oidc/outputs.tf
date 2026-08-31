output "github_actions_role_arn" {
  value = aws_iam_role.github_actions.arn
}

output "github_actions_terraform_role_arn" {
  value = var.create_terraform_role ? aws_iam_role.github_actions_terraform[0].arn : null
}

output "github_actions_db_power_role_arn" {
  value = var.create_db_power_role ? aws_iam_role.github_actions_db_power[0].arn : null
}

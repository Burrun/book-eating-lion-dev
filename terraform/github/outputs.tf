output "managed_environments" {
  value = keys(github_repository_environment.this)
}

output "managed_variable_names" {
  value = sort(distinct([for item in values(local.environment_variables) : item.name]))
}

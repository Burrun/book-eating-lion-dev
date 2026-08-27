output "db_endpoint" {
  value = data.aws_instance.dev_postgres.private_dns
}

output "database_name" {
  value = var.database_name
}

output "valkey_endpoint" {
  value = module.cache_valkey.valkey_primary_endpoint
}

output "cognito_user_pool_id" {
  value = module.auth.user_pool_id
}

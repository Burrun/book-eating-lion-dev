output "db_endpoint" {
  value = module.ec2_postgres.cluster_endpoint
}

output "valkey_endpoint" {
  value = module.cache_valkey.valkey_primary_endpoint
}

output "cognito_user_pool_id" {
  value = module.auth.user_pool_id
}

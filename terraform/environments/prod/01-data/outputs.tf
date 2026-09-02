output "db_endpoint" {
  value = module.database_private_dns.writer_fqdn
}

output "db_reader_endpoint" {
  value = module.database_private_dns.reader_fqdn
}

output "rds_proxy_endpoint" {
  value = module.rds_proxy.proxy_endpoint
}

output "valkey_endpoint" {
  value = module.cache_valkey.valkey_primary_endpoint
}

output "cognito_user_pool_id" {
  value = module.auth.user_pool_id
}

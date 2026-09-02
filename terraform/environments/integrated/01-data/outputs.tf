output "db_endpoint" {
  description = "앱이 쓰는 쓰기 엔드포인트 (RDS Proxy를 가리키는 사설 FQDN)"
  value       = module.database_private_dns.writer_fqdn
}

output "db_reader_endpoint" {
  description = "읽기 엔드포인트 (리드 리플리카를 가리키는 사설 FQDN)"
  value       = module.database_private_dns.reader_fqdn
}

output "db_master_secret_arn" {
  description = "00-init.sql 실행/데이터 이관용 마스터 계정 시크릿"
  value       = module.rds_postgres.master_user_secret_arn
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

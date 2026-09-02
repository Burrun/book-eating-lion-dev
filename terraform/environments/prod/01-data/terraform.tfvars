environment = "prod"
aws_region  = "ap-northeast-2"

database_name   = "bookdb_prod"
master_username = "bookadmin"
reader_count    = 1

aurora_deletion_protection     = true
aurora_skip_final_snapshot     = false
aurora_backup_retention_period = 7

valkey_node_type     = "cache.t4g.medium"
valkey_replica_count = 1

user_pool_name        = "lion-team3-prod"
cognito_domain_prefix = "book-eating-lion-prod" # 도메인 프리픽스는 리전 내 전역 고유 필요 - 충돌 방지 위해 유지

cognito_callback_urls = ["https://book.ajttk.com/auth/callback"]
cognito_logout_urls   = ["https://book.ajttk.com/"]

environment = "prod"
aws_region  = "ap-northeast-2"

database_name   = "bookdb"
master_username = "bookadmin"
reader_count    = 1

aurora_deletion_protection = true
aurora_skip_final_snapshot = false

valkey_node_type     = "cache.t4g.medium"
valkey_replica_count = 1

user_pool_name        = "lion-team3-prod"
cognito_domain_prefix = "book-eating-lion-prod" # 도메인 프리픽스는 리전 내 전역 고유 필요 - 충돌 방지 위해 유지

# TODO: 실제 프론트엔드 도메인으로 교체 (00-base domain_name과 맞출 것)
cognito_callback_urls = ["https://book-eating-lion.com/auth/callback"]
cognito_logout_urls   = ["https://book-eating-lion.com/"]

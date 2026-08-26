environment = "integrated"
aws_region  = "ap-northeast-2"

database_name   = "bookdb_prod"
master_username = "bookadmin" # dev EC2 인스턴스에 이미 있는 계정과 반드시 일치해야 함

valkey_node_type     = "cache.t4g.medium"
valkey_replica_count = 1

user_pool_name        = "lion-team3-prod"
cognito_domain_prefix = "book-eating-lion-integrated" # prod/01-data와 같은 값 쓰면 리전 내 전역 유일 제약에 걸려 나중에 prod apply 시 충돌남 (Sourcery PR #98 지적)

cognito_callback_urls = ["https://book.ajttk.com/auth/callback"]
cognito_logout_urls   = ["https://book.ajttk.com/"]

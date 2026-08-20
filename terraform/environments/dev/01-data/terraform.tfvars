environment = "dev"
aws_region  = "ap-northeast-2"

database_name   = "bookdb"
master_username = "bookadmin"

ec2_postgres_instance_type = "t4g.micro"

valkey_node_type     = "cache.t4g.medium"
valkey_replica_count = 0

user_pool_name        = "lion-team3-dev"
cognito_domain_prefix = "book-eating-lion-dev" # 도메인 프리픽스는 리전 내 전역 고유 필요 - 충돌 방지 위해 유지

# TODO: 실제 dev 프론트엔드 도메인으로 교체
cognito_callback_urls = ["https://dev.book-eating-lion.com/auth/callback"]
cognito_logout_urls   = ["https://dev.book-eating-lion.com/"]

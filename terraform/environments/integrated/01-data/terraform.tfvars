environment = "integrated"
aws_region  = "ap-northeast-2"

database_name   = "bookdb_prod"
master_username = "bookadmin" # RDS 마스터. 앱은 이 계정을 쓰지 않는다(서비스 계정 4개 사용)

# 비교 대상인 dev EC2 Postgres와 같은 급으로 맞춘다 (t4g.micro / gp3 30GiB / pg16).
# 이 값을 바꾸면 "EC2 대비 RDS" 비교 조건이 깨진다.
rds_instance_class    = "db.t4g.micro"
rds_allocated_storage = 30
rds_engine_version    = "16.14"
rds_multi_az          = false

valkey_node_type     = "cache.t4g.medium"
valkey_replica_count = 1

user_pool_name        = "lion-team3-prod"
cognito_domain_prefix = "book-eating-lion-integrated" # prod/01-data와 같은 값 쓰면 리전 내 전역 유일 제약에 걸려 나중에 prod apply 시 충돌남 (Sourcery PR #98 지적)

cognito_callback_urls = ["https://book.ajttk.com/auth/callback"]
cognito_logout_urls   = ["https://book.ajttk.com/"]

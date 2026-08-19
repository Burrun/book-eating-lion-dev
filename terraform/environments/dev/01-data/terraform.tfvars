environment = "dev"
aws_region  = "ap-northeast-2"

database_name   = "bookdb"
master_username = "bookadmin"

ec2_postgres_instance_type = "t4g.micro"

valkey_node_type     = "cache.t4g.medium"
valkey_replica_count = 0

user_pool_name        = "book-eating-lion-dev"
cognito_domain_prefix = "book-eating-lion-dev"

# TODO: 실제 dev 프론트엔드 도메인으로 교체
cognito_callback_urls = ["https://dev.book-eating-lion.com/auth/callback"]
cognito_logout_urls   = ["https://dev.book-eating-lion.com/"]

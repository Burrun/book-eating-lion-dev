# ⚠️ 실제 apply 전에 아래 값들을 확인/교체하세요.
# domain_name, frontend_bucket_name, media_bucket_name, alert_email 은 placeholder입니다.

environment = "prod"
aws_region  = "ap-northeast-2"

vpc_cidr            = "10.0.0.0/16"
availability_zones  = ["ap-northeast-2a", "ap-northeast-2c"]
public_subnet_cidrs = ["10.0.0.0/24", "10.0.1.0/24"]
app_subnet_cidrs    = ["10.0.10.0/24", "10.0.11.0/24"]
data_subnet_cidrs   = ["10.0.20.0/24", "10.0.21.0/24"]

# TODO: 실제 등록된 도메인으로 교체
domain_name = "book-eating-lion.com"

waf_rate_limit = 2000

# TODO: S3 버킷 이름은 전역 유일이어야 함 — 실제 배포 전 고유한 이름으로 교체
frontend_bucket_name = "book-eating-lion-prod-frontend"
media_bucket_name    = "book-eating-lion-prod-media"

service_names = ["catalog", "order", "member", "ai"]

# TODO: 실제 운영자 이메일로 교체
alert_email = "ops@book-eating-lion.com"

# github.com/Burrun/book-eating-lion-dev 기준
github_org  = "Burrun"
github_repo = "book-eating-lion-dev"

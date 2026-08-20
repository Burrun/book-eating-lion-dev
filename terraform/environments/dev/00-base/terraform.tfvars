# ⚠️ 실제 apply 전에 아래 값들을 확인/교체하세요.
# domain_name, frontend_bucket_name, media_bucket_name, alert_email 은 placeholder입니다.

environment = "dev"
aws_region  = "ap-northeast-2"

# prod와 CIDR을 완전히 분리 (같은 계정 안에 둘 다 떠 있어도 겹치지 않게)
vpc_cidr            = "10.1.0.0/16"
availability_zones  = ["ap-northeast-2a", "ap-northeast-2c"]
public_subnet_cidrs = ["10.1.0.0/24", "10.1.1.0/24"]
app_subnet_cidrs    = ["10.1.10.0/24", "10.1.11.0/24"]
data_subnet_cidrs   = ["10.1.20.0/24", "10.1.21.0/24"]

# TODO: 실제 서브도메인으로 교체 (prod와 같은 Route53 Zone을 재사용하지 않고 별도 Zone)
domain_name = "dev.book-eating-lion.com"

waf_rate_limit = 2000

# TODO: S3 버킷 이름은 전역 유일이어야 함 — 실제 배포 전 고유한 이름으로 교체
frontend_bucket_name = "book-eating-lion-dev-frontend"
media_bucket_name    = "book-eating-lion-dev-media"

service_names = ["catalog", "order", "member", "ai"]

# TODO: 실제 운영자 이메일로 교체
alert_email = "ops@book-eating-lion.com"

# github.com/Burrun/book-eating-lion-dev 기준
github_org  = "Burrun"
github_repo = "book-eating-lion-dev"

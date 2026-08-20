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

# github.com/Burrun/book-eating-lion-dev 기준. dev/prod가 별도 repo가 아니라
# 이 저장소 하나를 같이 쓴다(확인 완료 - 계정에 별도 prod repo 없음) - 그래서 prod도
# 여기(dev 리포지토리명)를 그대로 신뢰 대상으로 지정한다. IAM Role 자체는 환경별로
# 분리돼 있으므로(github_oidc 모듈이 이름에 environment를 넣음) repo가 같아도
# dev/prod 배포 권한은 섞이지 않는다.
github_org  = "Burrun"
github_repo = "book-eating-lion-dev"

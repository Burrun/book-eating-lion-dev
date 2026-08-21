# ⚠️ 실제 apply 전에 아래 값들을 확인/교체하세요.
# domain_name, frontend_bucket_name, media_bucket_name, alert_email 은 placeholder입니다.

environment = "dev"
aws_region  = "ap-northeast-2"

# dev는 10.13.0.0/16 대역 사용(2026-08-21 별도 승인받음). prod(10.14.0.0/16)와
# 겹치지 않게 분리해서 dev/prod 동시 운영도 가능.
vpc_cidr            = "10.13.0.0/16"
availability_zones  = ["ap-northeast-2a", "ap-northeast-2c"]
public_subnet_cidrs = ["10.13.0.0/24", "10.13.1.0/24"]
app_subnet_cidrs    = ["10.13.10.0/24", "10.13.11.0/24"]
data_subnet_cidrs   = ["10.13.20.0/24", "10.13.21.0/24"]

# ajttk.com은 이미 이 계정에 등록된 실제 도메인(계정 소유자 명의, Route53 Domains).
# apex/api/grafana는 예전 프로젝트가 쓰던 레코드라 이 프로젝트는 건드리지 않고
# dev 전용 서브도메인만 새로 씀 (prod와 같은 Zone을 재사용하지 않고 별도 Zone).
domain_name        = "dev.ajttk.com"
parent_zone_domain = "ajttk.com"

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

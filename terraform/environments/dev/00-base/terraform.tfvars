# ⚠️ 실제 apply 전에 아래 값들을 확인/교체하세요.
# domain_name, frontend_bucket_name, media_bucket_name, alert_email 은 placeholder입니다.

environment = "dev"
aws_region  = "ap-northeast-2"

# 10.14.0.0/16은 이정제님에게 할당된 CIDR 대역(계정 공유 IP 충돌 방지용, 팀원별로
# 따로 할당돼 있음). dev/prod가 동시에 뜨는 일이 없어서(비용 문제로 필요할 때만
# 번갈아 apply) 둘 다 같은 대역을 그대로 씀 - 동시 운영이 필요해지면 그때 다른
# 팀원 대역을 하나 더 받아서 분리할 것.
vpc_cidr            = "10.14.0.0/16"
availability_zones  = ["ap-northeast-2a", "ap-northeast-2c"]
public_subnet_cidrs = ["10.14.0.0/24", "10.14.1.0/24"]
app_subnet_cidrs    = ["10.14.10.0/24", "10.14.11.0/24"]
data_subnet_cidrs   = ["10.14.20.0/24", "10.14.21.0/24"]

# ajttk.com은 이미 이 계정에 등록된 실제 도메인(이정제님 소유, Route53 Domains).
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

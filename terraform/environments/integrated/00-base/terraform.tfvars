# ⚠️ apply 전 확인할 것: frontend_bucket_name/media_bucket_name은 전역 유일해야 함.

environment = "integrated"
aws_region  = "ap-northeast-2"

# prod 서비스 도메인. dev.ajttk.com은 여기서 안 건드림 (main.tf 상단 주석 참고).
domain_name        = "book.ajttk.com"
parent_zone_domain = "ajttk.com"

waf_rate_limit   = 2000
waf_ip_allowlist = ["118.217.76.39/32"]

frontend_bucket_name = "book-eating-lion-prod-frontend"
media_bucket_name    = "book-eating-lion-prod-media"

service_names = ["catalog", "order", "member", "ai"]

github_org  = "Burrun"
github_repo = "book-eating-lion-dev"

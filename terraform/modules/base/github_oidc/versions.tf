# tls 프로바이더로 GitHub OIDC 엔드포인트의 실제 TLS 인증서 지문을 가져온다.
# 지문을 하드코딩하지 않는 이유: GitHub가 인증서를 교체하면 하드코딩된 값은 조용히 깨진다.
terraform {
  required_providers {
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

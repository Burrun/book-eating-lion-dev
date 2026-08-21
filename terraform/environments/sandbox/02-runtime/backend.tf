terraform {
  required_version = ">= 1.8.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.31"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.14"
    }
    time = {
      source  = "hashicorp/time"
      version = "~> 0.11"
    }
  }

  # dev와 같은 state 버킷/락 테이블을 쓰되 key만 분리한다 - 별도 부트스트랩 불필요,
  # dev/02-runtime의 state와 섞이지 않는다.
  backend "s3" {
    bucket         = "book-eating-lion-tfstate-dev"
    key            = "sandbox/02-runtime.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "book-eating-lion-tflock-dev"
    encrypt        = true
  }
}

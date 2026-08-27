terraform {
  required_version = ">= 1.8.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
    github = {
      source  = "integrations/github"
      version = "~> 6.0"
    }
  }

  backend "s3" {
    bucket         = "book-eating-lion-tfstate-integrated"
    key            = "github/bootstrap.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "book-eating-lion-tflock-integrated"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region
}

# 인증 토큰은 코드나 tfvars에 저장하지 않는다. provider가 GITHUB_TOKEN 환경
# 변수를 읽으며, 로컬에서는 `GITHUB_TOKEN="$(gh auth token)" terraform ...`로 실행한다.
provider "github" {
  owner = var.github_owner
}

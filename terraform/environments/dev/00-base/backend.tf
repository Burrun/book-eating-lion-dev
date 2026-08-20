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
  }

  backend "s3" {
    bucket         = "book-eating-lion-tfstate-dev"
    key            = "dev/00-base.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "book-eating-lion-tflock-dev"
    encrypt        = true
  }
}

terraform {
  required_version = ">= 1.8.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  backend "s3" {
    bucket         = "book-eating-lion-tfstate-dev"
    key            = "dev/01-data.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "book-eating-lion-tflock-dev"
    encrypt        = true
  }
}

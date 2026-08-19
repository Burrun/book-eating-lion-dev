provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "book-eating-lion"
      Environment = var.environment
      ManagedBy   = "terraform"
      Layer       = "data"
    }
  }
}

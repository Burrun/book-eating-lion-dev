provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "lion"
      Team        = "Team3"
      Owner       = "이정제"
      Environment = var.environment
      ManagedBy   = "terraform"
      Layer       = "data"
    }
  }
}

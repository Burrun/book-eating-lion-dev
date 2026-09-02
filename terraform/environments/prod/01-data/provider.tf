provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "lion"
      Team        = "Team3"
      Owner       = "likelion-cloud6-team3"
      Environment = var.environment
      ManagedBy   = "terraform"
      Layer       = "data"
    }
  }
}

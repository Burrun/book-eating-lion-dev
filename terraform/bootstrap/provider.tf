terraform {
  required_version = ">= 1.8.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
  }

  # 이 모듈은 dev/prod의 나머지 모든 계층이 쓸 S3 tfstate 백엔드 자체를
  # 만든다 - 그 백엔드가 아직 없는 상태에서 실행돼야 하므로(닭-달걀 문제)
  # local state로 관리한다. state 파일(terraform.tfstate)은 .gitignore에
  # 걸려 커밋되지 않으니, 실행한 사람이 로컬에 보관하거나 팀 채널 등
  # 별도 위치에 백업해 둘 것 - 잃어버리면 다음 실행 때 버킷/테이블을
  # import로 다시 끌어와야 한다.
}

provider "aws" {
  region = "ap-northeast-2"

  default_tags {
    tags = {
      Project   = "lion"
      Owner     = "likelion-cloud6-team3"
      Team      = "Team3"
      ManagedBy = "terraform"
      Layer     = "bootstrap"
    }
  }
}

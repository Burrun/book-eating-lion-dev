# dev/prod의 VPC CIDR 플랜을 이 파일 한 곳에서만 관리한다. 각 환경의
# terraform.tfvars에 CIDR을 중복 정의하지 않고 여기서 environment로 조회해서
# 쓴다 - 대역을 바꾸거나 새 환경을 추가할 땐 이 locals만 고치면 된다.
#
# dev(10.13.0.0/16)와 prod(10.14.0.0/16)는 계정 소유자에게 할당된 서로 다른
# CIDR 대역이라 겹치지 않는다 - dev/prod 동시 운영도 가능하다.
locals {
  cidr_plans = {
    dev = {
      vpc_cidr            = "10.13.0.0/16"
      availability_zones  = ["ap-northeast-2a", "ap-northeast-2c"]
      public_subnet_cidrs = ["10.13.0.0/24", "10.13.1.0/24"]
      app_subnet_cidrs    = ["10.13.10.0/24", "10.13.11.0/24"]
      data_subnet_cidrs   = ["10.13.20.0/24", "10.13.21.0/24"]
    }
    prod = {
      vpc_cidr            = "10.14.0.0/16"
      availability_zones  = ["ap-northeast-2a", "ap-northeast-2c"]
      public_subnet_cidrs = ["10.14.0.0/24", "10.14.1.0/24"]
      app_subnet_cidrs    = ["10.14.10.0/24", "10.14.11.0/24"]
      data_subnet_cidrs   = ["10.14.20.0/24", "10.14.21.0/24"]
    }
  }

  plan = local.cidr_plans[var.environment]
}

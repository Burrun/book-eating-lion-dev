variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "app_subnet_id" {
  description = "인스턴스를 배치할 서브넷. data_subnet이 아니라 app_subnet을 쓴다 - data subnet은 NAT가 없어 패키지 설치가 안 됨"
  type        = string
}

variable "app_security_group_id" {
  description = "이 SG를 가진 것만 5432로 접속 허용 (aurora_pg와 동일한 접근 통제 원칙)"
  type        = string
}

variable "instance_type" {
  type    = string
  default = "t4g.micro"
}

variable "root_volume_size_gb" {
  type    = number
  default = 20
}

variable "database_name" {
  type    = string
  default = "bookdb"
}

variable "master_username" {
  type    = string
  default = "bookadmin"
}

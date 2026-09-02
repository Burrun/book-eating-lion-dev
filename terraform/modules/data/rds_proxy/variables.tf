variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "data_subnet_ids" {
  type = list(string)
}

variable "app_security_group_id" {
  type = string
}

variable "cluster_security_group_id" {
  description = "대상 DB(aurora_pg 클러스터 또는 rds_postgres 인스턴스)의 SG - Proxy SG에서 이 SG로 5432 인바운드를 열어줘야 함"
  type        = string
}

# 타깃은 Aurora 클러스터 또는 단일 RDS 인스턴스 중 하나다.
# aws_db_proxy_target의 precondition이 "정확히 하나만" 지정됐는지 검사한다.
variable "aurora_cluster_identifier" {
  description = "Aurora 클러스터를 프록시할 때만 지정. 단일 RDS 인스턴스면 null로 두고 db_instance_identifier를 쓴다"
  type        = string
  default     = null
}

variable "db_instance_identifier" {
  description = "단일 RDS 인스턴스를 프록시할 때만 지정. Aurora면 null로 두고 aurora_cluster_identifier를 쓴다"
  type        = string
  default     = null
}

variable "secrets_manager_arn" {
  description = "마스터 계정 secret. Proxy가 항상 등록해두는 기본 자격증명이다"
  type        = string
}

# RDS Proxy는 auth에 등록된 계정만 통과시킨다. 앱은 catalog_svc/order_svc/member_svc/
# ai_svc로 붙는데 마스터 secret 하나만 등록해두면 그 4개가 전부 인증 거부된다
# (2026-09-02 integrated prod 전환 준비 중 발견 - prod는 아직 실제로 DB를 안 붙여봐서
# 드러나지 않았던 결함). 서비스 계정 secret을 여기로 전부 넘겨야 앱이 붙는다.
variable "additional_auth_secret_arns" {
  description = "마스터 외에 Proxy를 통과시킬 계정 secret ARN 목록 (서비스 계정 4개)"
  type        = list(string)
  default     = []
}

variable "environment" {
  type    = string
  default = "integrated"
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

# ── DB: 새 인스턴스를 안 만든다 (main.tf 참고) ────────────────────
variable "database_name" {
  description = "dev EC2 Postgres 인스턴스 안에 새로 만들 논리 DB 이름"
  type        = string
  default     = "bookdb_prod"
}

variable "master_username" {
  description = "dev EC2 인스턴스에 이미 존재하는 마스터 계정과 반드시 같아야 한다 (새로 만드는 게 아니라 기존 계정으로 createdb만 함)"
  type        = string
  default     = "bookadmin"
}

# ── Valkey (prod 전용 - 새로 만듦, dev와 공유 안 함) ────────────────
variable "valkey_node_type" {
  type    = string
  default = "cache.t4g.medium"
}

variable "valkey_replica_count" {
  type    = number
  default = 1
}

# ── Cognito (prod 전용 User Pool - 새로 만듦) ───────────────────────
variable "user_pool_name" {
  type = string
}

variable "cognito_domain_prefix" {
  type = string
}

variable "cognito_callback_urls" {
  type = list(string)
}

variable "cognito_logout_urls" {
  type = list(string)
}

# ── RDS (2026-09-02 EC2 Postgres에서 분리) ──────────────────────────
# 기본값은 비교 대상인 dev EC2(t4g.micro / gp3 30GiB / postgresql16)와 같은 급이다.
variable "rds_engine_version" {
  type    = string
  default = "16.14"
}

variable "rds_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "rds_allocated_storage" {
  type    = number
  default = 30
}

variable "rds_multi_az" {
  description = "EC2(단일)와 비교 조건을 맞추려고 false. 운영 승격 시 true"
  type        = bool
  default     = false
}

variable "rds_backup_retention_period" {
  type    = number
  default = 7
}

variable "rds_deletion_protection" {
  type    = bool
  default = true
}

variable "rds_skip_final_snapshot" {
  type    = bool
  default = false
}

variable "rds_apply_immediately" {
  description = "비교 실험 중 인스턴스 클래스를 바꿔가며 볼 거라 true"
  type        = bool
  default     = true
}

# catalog-api에 RoutingDataSourceConfig(app.datasource.writer/reader,
# @Transactional(readOnly=true) 기준 자동 분기)가 들어간 뒤에만 1 이상으로
# 올린다. 그 전에 올리면 catalog의 쓰기(리뷰/찜/관리자 CRUD)와 startup
# Liquibase가 read-only 트랜잭션 에러로 죽는다 - modules/data/rds_postgres의
# read_replica_count 주석 참고.
variable "rds_read_replica_count" {
  type    = number
  default = 0
}

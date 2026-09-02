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
  description = "01-data 보안그룹들이 인바운드 소스로 참조하는 EKS 노드/Pod 공용 SG (00-base vpc 출력)"
  type        = string
}

# 마이너 버전은 리전이 실제로 제공하는 것이어야 한다 - 16.8은 ap-northeast-2에
# 없어서 CreateDBInstance가 InvalidParameterCombination으로 죽었다(2026-09-02).
#   aws rds describe-db-engine-versions --engine postgres --region ap-northeast-2
# 현재 가용: 16.9 ~ 16.15. aurora_pg 모듈 기본값과 같은 16.14로 맞춘다.
variable "engine_version" {
  description = "PostgreSQL 엔진 버전. EC2 쪽이 postgresql16-server라 메이저 16을 맞춘다"
  type        = string
  default     = "16.14"
}

variable "instance_class" {
  description = "비교 대상 EC2가 t4g.micro(2vCPU/1GiB Graviton)라 기본값을 같은 급으로 둔다"
  type        = string
  default     = "db.t4g.micro"
}

variable "allocated_storage" {
  description = "GiB. EC2 루트 볼륨(gp3 30GiB)과 동일"
  type        = number
  default     = 30
}

variable "max_allocated_storage" {
  description = "스토리지 오토스케일링 상한(GiB). allocated_storage와 같게 두면 오토스케일링이 꺼진다"
  type        = number
  default     = 100
}

# 기본 0인 이유는 리플리카가 필요 없어서가 아니라, 앱이 아직 리플리카를 감당하지
# 못하기 때문이다. k8s/catalog/configmap.yaml이 DB_HOST를 db-reader-service로
# 잡고 있어서 catalog가 서비스 통째로 reader를 본다 - 리플리카(read-only)를 붙이면
# catalog의 쓰기(리뷰/찜/관리자 등록)와 startup Liquibase가 전부
# "cannot execute ... in a read-only transaction"으로 죽는다.
#
# 1로 올리기 전에 요청 단위 read/write 라우팅이 먼저 들어가야 한다
# (docs/TODOS.md의 read/write 분리 항목 참고). 그게 끝나면 이 값만 바꾸면 된다.
variable "read_replica_count" {
  description = "리드 리플리카 개수. 앱에 read/write 라우팅이 들어가기 전까지는 0이어야 한다"
  type        = number
  default     = 0
}

variable "replica_instance_class" {
  description = "리플리카 인스턴스 클래스. null이면 소스(instance_class)와 동급"
  type        = string
  default     = null
}

variable "multi_az" {
  description = "EC2(단일 인스턴스)와 비교 조건을 맞추려고 기본값 false. 운영 승격 시 true"
  type        = bool
  default     = false
}

variable "database_name" {
  type    = string
  default = "bookdb"
}

variable "master_username" {
  type    = string
  default = "bookadmin"
}

variable "sns_topic_arn" {
  description = "CloudWatch 알람 액션 대상 (00-base alerting 출력)"
  type        = string
}

variable "backup_retention_period" {
  description = "자동 백업(PITR) 보존 기간(일). 미지정 시 AWS 기본값(1일)이 적용되어 24시간이 지난 장애는 PITR로 복구할 수 없다."
  type        = number
  default     = 1
}

variable "deletion_protection" {
  type    = bool
  default = true
}

variable "skip_final_snapshot" {
  type    = bool
  default = false
}

variable "apply_immediately" {
  description = "false면 인스턴스 클래스/스토리지 변경이 다음 유지관리 창까지 미뤄진다. 비교 실험 중엔 true가 편하다"
  type        = bool
  default     = false
}

variable "connection_alarm_threshold" {
  description = "db.t4g.micro의 max_connections는 약 112다(메모리 비례). aurora_pg의 200을 그대로 쓰면 절대 안 울린다"
  type        = number
  default     = 90
}

variable "freeable_memory_alarm_bytes" {
  description = "기본 100MiB. 1GiB 인스턴스에서 이 아래로 내려가면 OOM/스왑 구간이다"
  type        = number
  default     = 104857600
}

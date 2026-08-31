variable "environment" {
  description = "환경 이름 (dev | prod) - IAM Role 이름 충돌 방지용"
  type        = string
}

variable "create_oidc_provider" {
  description = "GitHub OIDC Provider(계정당 유일)를 이 호출에서 만들지 여부. 이 계정은 여러 팀이 공유하고 있고 이미 (이 프로젝트와 무관한) Provider가 그 URL에 존재해서(2026-08-20 확인) dev/prod 둘 다 false로 두고 기존 걸 조회만 한다 - true로 두면 IAM EntityAlreadyExists로 apply가 실패한다. 이 계정을 벗어나 완전히 새 계정에서 쓸 때만 한쪽을 true로"
  type        = bool
  default     = false
}

variable "github_org" {
  description = "GitHub organization/사용자명 (예: book-eating-lion-team)"
  type        = string
}

variable "github_repo" {
  description = "이 역할을 쓸 수 있는 리포지토리 이름 (org/repo 중 repo 부분)"
  type        = string
}

variable "ecr_repository_arns" {
  description = "GitHub Actions가 push할 수 있는 ECR 레포지토리 ARN 목록 (00-base의 container_reg 출력)"
  type        = list(string)
}

variable "frontend_bucket_arn" {
  description = "Frontend → S3 & CloudFront 잡의 `aws s3 sync --delete`가 쓰는 버킷 ARN (storage 모듈과 같은 계층이라 직접 참조)"
  type        = string
}

variable "media_bucket_arn" {
  description = "catalog 등이 업로드에 쓰는 버킷 ARN"
  type        = string
}

variable "cloudfront_distribution_arn" {
  description = "CloudFront invalidation 권한 스코프. Distribution은 02-runtime에서 만들어져 ID를 미리 예측할 수 없다 - 02-runtime 배포 후 이 값을 채우면 그 배포로 좁혀진다. null이면(부트스트랩 등) 계정 스코프로만 제한(distribution/*)"
  type        = string
  default     = null
}

variable "extra_ssm_read_prefixes" {
  description = <<-EOT
    이 role의 SSM 읽기 권한(ReadEnvironmentDataParameters)에 var.environment 말고
    추가로 허용할 환경 prefix 목록 (예: ["dev"]).

    왜 필요한가: integrated 클러스터가 dev/prod를 namespace로 같이 서빙하지만,
    dev의 실제 DB(01-data)는 마이그레이션 대상이 아니라서 여전히 /dev/data/*
    에 있다. main-cd.yml의 "Load database endpoints" 스텝은 DEPLOY_ENV(dev|prod)
    기준으로 /$${DEPLOY_ENV}/data/*를 읽는데, integrated role은 기본적으로
    /integrated/data/*만 허용돼 있어 dev 배포 시 AccessDenied가 난다
    (2026-08-26 실제로 겪음 - Main CD #65 "Load database endpoints" 실패).
  EOT
  type        = list(string)
  default     = []
}

variable "extra_frontend_bucket_arns" {
  description = "FrontendBucketList/Objects에 var.frontend_bucket_arn 말고 추가로 허용할 버킷 ARN 목록 (extra_ssm_read_prefixes와 같은 이유 - dev 프론트엔드 버킷은 마이그레이션 대상이 아니라서 integrated role이 기본적으로 접근 못 함)"
  type        = list(string)
  default     = []
}

variable "extra_media_bucket_arns" {
  description = "FrontendBucketList/Objects에 var.media_bucket_arn 말고 추가로 허용할 버킷 ARN 목록 (extra_frontend_bucket_arns와 동일한 이유)"
  type        = list(string)
  default     = []
}

variable "eks_cluster_name" {
  description = "eks:DescribeCluster를 이 이름으로 스코프하기 위한 값. 02-runtime의 eks_cluster 모듈이 실제로 쓸 cluster_name과 반드시 같아야 한다 - 00-base가 02-runtime을 참조할 수 없어(계층 역방향 의존 금지) 값을 호출부에서 명시적으로 맞춰준다. 인프라구성명세.md §4.1 네이밍 패턴(lion-team3-{environment})을 따를 것"
  type        = string
}

# eks_cluster_name은 실제 클러스터의 Terraform 출력이 아니라 "이 이름으로 만들
# 예정"이라는 문자열일 뿐이다 — 클러스터는 02-runtime에서 만들어지는데, 00-base가
# 그 모듈 출력을 입력으로 받으면 00-base -> 02-runtime 역방향 의존이 생긴다
# (edge_routing/dns_zone과 같은 원칙: 대상이 없는 계층에서 그 대상을 참조하지 않는다).

variable "create_terraform_role" {
  description = "terraform-apply.yml/terraform-destroy.yml(GitHub Actions)이 쓸 IAM Role을 이 호출에서 만들지 여부. 계정당 한 번만(integrated에서만) true로 둔다."
  type        = bool
  default     = false
}

variable "create_db_power_role" {
  description = "야간 비용 절감용 DB EC2 stop/start 워크플로(db-power.yml)가 쓸 IAM Role을 이 호출에서 만들지 여부. 계정당 한 번만(integrated에서만) true로 둔다. environment로 이름을 나누지 않는다 - 인스턴스 하나만 대상이라 환경별로 쪼갤 이유가 없다."
  type        = bool
  default     = false
}

variable "db_power_instance_id" {
  description = "create_db_power_role = true일 때 start/stop을 허용할 EC2 인스턴스 ID 하나 (예: i-07e730fed45de433b). 이 인스턴스 하나로만 권한을 좁힌다."
  type        = string
  default     = null
}

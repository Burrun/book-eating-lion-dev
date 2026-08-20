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
  description = "main-cd.yml의 Frontend → S3 & CloudFront 잡이 `aws s3 sync --delete`로 쓰는 버킷 ARN. storage 모듈과 이 모듈이 같은 계층(00-base)이라 역방향 의존 없이 바로 참조 가능"
  type        = string
}

variable "media_bucket_arn" {
  description = "catalog 서비스 등이 업로드에 쓰는 버킷 ARN - CI가 배포 과정에서 직접 쓰진 않지만 향후 자산 업로드 파이프라인을 CI에 넣을 걸 대비해 같이 부여"
  type        = string
}

variable "cloudfront_distribution_arn" {
  description = "CloudFront 캐시 무효화(invalidation) 권한 스코프용. CloudFront 배포는 02-runtime(edge_routing)에서 만들어지고 Distribution ID는 AWS가 생성 시점에 임의로 부여해서(eks_cluster_name처럼 이름을 미리 못 예측함) 00-base가 값을 미리 알 수 없다 - null이면 리소스를 * 로 열어서 하위 계층 완료 전에도 apply 가능하게 한다(계정 공유라 다른 팀 CloudFront도 무효화 가능하다는 트레이드오프 - ALB/Karpenter IAM * 와 같은 결의 accepted risk)"
  type        = string
  default     = null
}

variable "eks_cluster_name" {
  description = "eks:DescribeCluster를 이 이름으로 스코프하기 위한 값. 02-runtime의 eks_cluster 모듈이 실제로 쓸 cluster_name과 반드시 같아야 한다 - 00-base가 02-runtime을 참조할 수 없어(계층 역방향 의존 금지) 값을 호출부에서 명시적으로 맞춰준다. 인프라구성명세.md §4.1 네이밍 패턴(lion-team3-{environment})을 따를 것"
  type        = string
}

# eks_cluster_name은 실제 클러스터의 Terraform 출력이 아니라 "이 이름으로 만들
# 예정"이라는 문자열일 뿐이다 — 클러스터는 02-runtime에서 만들어지는데, 00-base가
# 그 모듈 출력을 입력으로 받으면 00-base -> 02-runtime 역방향 의존이 생긴다
# (edge_routing/dns_zone과 같은 원칙: 대상이 없는 계층에서 그 대상을 참조하지 않는다).

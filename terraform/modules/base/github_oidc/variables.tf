variable "environment" {
  description = "환경 이름 (dev | prod) - IAM Role 이름 충돌 방지용"
  type        = string
}

variable "create_oidc_provider" {
  description = "GitHub OIDC Provider(계정당 유일)를 이 호출에서 만들지 여부. dev/prod 중 하나만 true로 둘 것"
  type        = bool
  default     = true
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

variable "eks_cluster_name" {
  description = "eks:DescribeCluster를 이 이름으로 스코프하기 위한 값. 02-runtime의 eks_cluster 모듈이 실제로 쓸 cluster_name과 반드시 같아야 한다 - 00-base가 02-runtime을 참조할 수 없어(계층 역방향 의존 금지) 값을 호출부에서 명시적으로 맞춰준다. 인프라구성명세.md §4.1 네이밍 패턴(lion-team3-{environment})을 따를 것"
  type        = string
}

# eks_cluster_name은 실제 클러스터의 Terraform 출력이 아니라 "이 이름으로 만들
# 예정"이라는 문자열일 뿐이다 — 클러스터는 02-runtime에서 만들어지는데, 00-base가
# 그 모듈 출력을 입력으로 받으면 00-base -> 02-runtime 역방향 의존이 생긴다
# (edge_routing/dns_zone과 같은 원칙: 대상이 없는 계층에서 그 대상을 참조하지 않는다).

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

# EKS 클러스터 ARN은 여기서 받지 않는다 — 클러스터는 02-runtime에서 만들어지는데,
# 00-base가 그 출력을 입력으로 받으면 00-base -> 02-runtime 역방향 의존이 생긴다
# (edge_routing/dns_zone과 같은 원칙: 대상이 없는 계층에서 그 대상을 참조하지 않는다).
# 대신 eks:Describe*는 리소스 전체에 허용한다 — 이건 kubeconfig를 만들기 위한
# 읽기 권한일 뿐이고, 실제 클러스터 내부 권한은 K8s RBAC(aws-auth/access entry)가 따로 막는다.

variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "app_subnet_ids" {
  description = "EKS Control Plane ENI + 시스템 노드그룹이 들어갈 Private App Subnet (2개)"
  type        = list(string)
}

variable "cluster_name" {
  type    = string
  default = null
}

variable "cluster_version" {
  type    = string
  default = "1.34" # 1.30은 지원 종료(2026-08-20 확인) - 신규 클러스터는 EKS 표준/연장 지원 목록에 있는 버전만 가능
}

variable "sns_topic_arn" {
  type = string
}

variable "system_node_instance_type" {
  description = "CoreDNS/Karpenter 컨트롤러 기동용 시스템 노드그룹 인스턴스 타입. taint가 없어 앱 워크로드 Pod도 여기 스케줄링될 수 있으므로 Karpenter NodePool의 아키텍처 요구사항(amd64)과 반드시 맞출 것 - 어긋나면 exec format error로 크래시루프"
  type        = string
  default     = "t3.medium"
}

variable "system_node_desired_size" {
  type    = number
  default = 2
}

variable "github_actions_role_arn" {
  description = "CI가 kubectl로 배포할 수 있도록 EKS Access Entry를 부여할 역할 (00-base SSM 출력). null이면 Access Entry를 만들지 않음"
  type        = string
  default     = null
}

variable "admin_principal_arns" {
  description = "kubectl/terraform으로 이 클러스터를 관리할 사람(들)의 IAM 사용자/역할 ARN 목록. bootstrap_cluster_creator_admin_permissions는 최초 생성 시점에만 적용되고 기존 클러스터에는 소급 적용이 안 되므로, 여기 등록된 principal에게 명시적으로 AmazonEKSClusterAdminPolicy를 부여한다"
  type        = list(string)
  default     = []
}

variable "public_access_cidrs" {
  description = "EKS API 서버 퍼블릭 엔드포인트 접근을 허용할 CIDR 목록. GitHub-hosted runner는 IP 대역이 넓어 기본값은 전체 허용이지만, self-hosted runner/VPN을 쓰게 되면 여기를 좁혀서 실제로 제한할 것"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

# ── 시스템 노드그룹 전용 taint ────────────────────────────────────
# 이 값들은 karpenter 모듈(그 taint를 견뎌야 하는 컨트롤러)도 정확히 같은
# key/effect를 알아야 하므로, 호출부(02-runtime main.tf)의 locals 하나에서
# 양쪽 모듈에 동일하게 전달한다(/code-review 지적사항 - 여러 모듈에 각각
# 하드코딩돼 있으면 나중에 값을 바꿀 때 한쪽만 바뀌어 조용히 어긋난다).
variable "system_pool_taint_key" {
  type    = string
  default = "CriticalAddonsOnly"
}

variable "system_pool_taint_value" {
  type    = string
  default = "true"
}

variable "system_pool_taint_effect" {
  description = "Kubernetes 표기(NoSchedule/NoExecute/PreferNoSchedule). aws_eks_node_group의 taint 블록엔 이 모듈이 자동으로 AWS API 표기(NO_SCHEDULE 등)로 변환해서 넣는다 - 두 표기가 달라서 호출부가 신경 쓸 필요 없게."
  type        = string
  default     = "NoSchedule"
}

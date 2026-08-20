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
  description = "CoreDNS/Karpenter 컨트롤러 기동용 시스템 노드그룹 인스턴스 타입. 이 노드그룹엔 taint가 없어서 앱 워크로드 Pod도 여기 스케줄링될 수 있다 - Karpenter NodePool의 amd64 요구사항과 아키텍처를 반드시 맞출 것(안 맞으면 시스템 노드그룹에 뜬 Pod만 exec format error로 크래시루프, 2026-08-20 실제로 겪음: karpenter NodePool은 고쳤는데 이 시스템 노드그룹이 여전히 arm64라 order/member Pod가 계속 여기 스케줄링돼서 안 풀렸었다)"
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

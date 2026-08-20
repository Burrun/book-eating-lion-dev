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
  default = "1.30"
}

variable "sns_topic_arn" {
  type = string
}

variable "system_node_instance_type" {
  description = "CoreDNS/Karpenter 컨트롤러 기동용 시스템 노드그룹 인스턴스 타입"
  type        = string
  default     = "t4g.medium"
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

variable "public_access_cidrs" {
  description = "EKS API 서버 퍼블릭 엔드포인트 접근을 허용할 CIDR 목록. GitHub-hosted runner는 IP 대역이 넓어 기본값은 전체 허용이지만, self-hosted runner/VPN을 쓰게 되면 여기를 좁혀서 실제로 제한할 것"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

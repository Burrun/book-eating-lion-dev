variable "environment" {
  type = string
}

variable "cluster_name" {
  type = string
}

variable "cluster_endpoint" {
  type = string
}

variable "oidc_provider_arn" {
  type = string
}

variable "oidc_provider_url" {
  description = "https:// 접두사가 붙은 전체 issuer URL"
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "app_subnet_ids" {
  description = "Karpenter가 노드를 띄울 Private App Subnet - 기획서 원칙대로 Private App Subnet에만 생성"
  type        = list(string)
}

variable "node_security_group_id" {
  description = "Karpenter가 만드는 노드에 붙일 SG (eks_cluster의 노드 SG 재사용 또는 별도)"
  type        = string
}

variable "karpenter_version" {
  type    = string
  default = "1.0.6"
}

variable "instance_types" {
  description = "Karpenter가 선택할 수 있는 인스턴스 패밀리 (스팟 가용성을 위해 여러 개 지정). amd64 패밀리로 지정할 것 - NodePool의 kubernetes.io/arch 요구사항과 반드시 맞춰야 한다(아래 주석 참고)"
  type        = list(string)
  default     = ["t3.medium", "t3.large", "m6i.large"]
}

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

variable "app_security_group_id" {
  description = "modules/base/vpc가 만드는 EKS 노드/Pod 공용 SG - Aurora/RDS Proxy/Valkey/EC2 Postgres 등 데이터 계층 SG가 이 SG발 트래픽만 허용하므로, 이걸 안 붙이면 노드가 DB에 연결하지 못한다(2026-08-21 실제로 겪음)."
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

# 시스템 노드그룹(eks_cluster 모듈)에 걸린 taint를 Karpenter 컨트롤러가 견뎌야
# 자기 자신이 만드는 노드가 아니라 그 노드그룹에 고정된다. 호출부가
# eks_cluster에 넘기는 값과 정확히 같은 값을 여기에도 넘겨야 하므로 기본값을
# 두지 않는다 - 값이 어긋나면(예: 호출부가 taint는 바꿨는데 여기는 깜빡함)
# 그냥 조용히 스케줄이 안 되는 대신, 호출부에서 값을 명시하도록 강제한다.
variable "system_pool_taint_key" {
  type = string
}

variable "system_pool_taint_effect" {
  description = "Kubernetes 표기(NoSchedule 등) - toleration.effect에 그대로 쓴다."
  type        = string
}

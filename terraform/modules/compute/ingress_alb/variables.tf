variable "environment" {
  type = string
}

variable "cluster_name" {
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

variable "public_subnet_ids" {
  description = "NLB가 뜰 Public Subnet - AWS Load Balancer Controller가 이 서브넷에 kubernetes.io/role/elb 태그를 보고 자동 선택함"
  type        = list(string)
}

variable "aws_region" {
  type = string
}

variable "alb_controller_chart_version" {
  description = "aws-load-balancer-controller Helm 차트 버전. 버전을 안 고정하면 업그레이드 때마다 IAM 정책/Helm set 값이 안 맞아 배포가 깨질 수 있다(2026-08-20 실제로 겪음 - DescribeListenerAttributes/ModifyListenerAttributes 권한 누락). 올릴 땐 릴리스 노트의 iam_policy.json과 대조해서 alb_controller.main.tf의 AllowReadOnly/AllowLoadBalancerWrite도 같이 갱신할 것"
  type        = string
  default     = "3.5.0"
}

variable "ingress_nginx_chart_version" {
  description = "ingress-nginx Helm 차트 버전. 검증된 값으로 고정 - 업그레이드는 명시적으로 이 값을 바꿔서 진행할 것"
  type        = string
  default     = "4.15.1"
}

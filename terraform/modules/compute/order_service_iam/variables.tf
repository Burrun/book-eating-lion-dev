variable "environment" {
  type = string
}

variable "namespace" {
  description = "이 Role을 trust할 k8s ServiceAccount의 네임스페이스 (ai_service_iam과 동일한 이유 - variables.tf 주석 참고)."
  type        = string
  default     = "lion-app"
}

variable "oidc_provider_arn" {
  type = string
}

variable "oidc_provider_url" {
  description = "https:// 접두사가 붙은 전체 issuer URL"
  type        = string
}

variable "purchase_channel_arn" {
  description = "구매 확정 이벤트 SQS 큐 ARN (01-data ai_pipeline 출력) - SqsBookPurchasePublisher가 발행"
  type        = string
}

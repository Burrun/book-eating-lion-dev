variable "environment" {
  type = string
}

variable "namespace" {
  description = <<-EOT
    이 Role을 trust할 k8s ServiceAccount의 네임스페이스.
    integrated 클러스터에서 dev/prod가 namespace로만 나뉘므로, 이 값을
    env별로 다르게 넘겨야 dev 파드가 prod Role을 assume하는 걸 막을 수 있다.
    (기존 dev/prod 분리-클러스터 환경은 namespace가 lion-app 하나뿐이라
    기본값을 유지하면 코드 변경 없이 그대로 동작한다.)
  EOT
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

variable "ingest_channel_arn" {
  type = string
}

variable "purchase_channel_arn" {
  description = "구매 확정 이벤트 SQS 큐 ARN (01-data ai_pipeline 출력) - ai-api SqsPurchaseListener가 소비"
  type        = string
}

variable "recommendation_index_arn" {
  description = "S3 Vectors provider 지원 전까지 null (01-data ai_pipeline 참고)"
  type        = string
  default     = null
}

variable "purchased_book_rag_index_arn" {
  type    = string
  default = null
}

variable "bedrock_model_arns" {
  description = "실제 사용하는 임베딩/LLM 모델 ARN (Titan Embeddings V2, Claude 등)"
  type        = list(string)
}

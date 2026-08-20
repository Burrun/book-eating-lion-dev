variable "environment" {
  type = string
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

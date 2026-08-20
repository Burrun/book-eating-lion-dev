output "ingest_channel_arn" {
  description = "신간 등록 이벤트 SQS 큐 ARN - 앱이 여기로 직접 SendMessage"
  value       = aws_sqs_queue.ingest.arn
}

output "purchase_channel_arn" {
  description = "구매 확정 이벤트 SQS 큐 ARN (order-api가 발행, ai-api가 소비)"
  value       = aws_sqs_queue.purchase.arn
}

output "purchase_channel_url" {
  description = "구매 확정 이벤트 SQS 큐 URL - SQS_PURCHASE_QUEUE_URL 변수에 그대로 씀"
  value       = aws_sqs_queue.purchase.id
}

# S3 Vectors provider 지원 전까지 null - main.tf 상단 주석 참고.
output "vector_bucket_arn" {
  value = null
}

output "recommendation_index_arn" {
  value = null
}

output "purchased_book_rag_index_arn" {
  value = null
}

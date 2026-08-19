output "ingest_channel_arn" {
  description = "신간 등록 이벤트 SQS 큐 ARN - 앱이 여기로 직접 SendMessage"
  value       = aws_sqs_queue.ingest.arn
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

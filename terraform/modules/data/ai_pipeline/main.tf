# ⚠️ 세부 구현 미확정 (TERRAFORM_STRUCTURE.md §3.2-5 참고).
#
# 실제 백엔드 코드(이벤트-메시징-명세.md)를 보면 신간 등록 이벤트는 S3 업로드 이벤트가
# 아니라 catalog-api가 직접 SQS로 발행하는 구조다. 그래서 S3 버킷 이벤트 알림은 여기서
# 만들지 않는다 - 앱이 SqsClient로 이 큐에 바로 SendMessage하면 된다.
resource "aws_sqs_queue" "ingest_dlq" {
  name                      = "book-eating-lion-${var.environment}-ai-ingest-dlq"
  message_retention_seconds = 1209600 # 14일 - 실패 메시지를 살펴볼 시간 확보
}

resource "aws_sqs_queue" "ingest" {
  name                       = "book-eating-lion-${var.environment}-ai-ingest"
  visibility_timeout_seconds = 300 # 임베딩은 청크당 1회, 장편은 200~500청크라 넉넉히

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.ingest_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "ingest_dlq" {
  queue_url = aws_sqs_queue.ingest_dlq.id

  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.ingest.arn]
  })
}

# ── S3 Vectors — 아직 못 만듦 ──────────────────────────────────────
# hashicorp/aws provider 5.100.0 기준 s3vectors_* 리소스 타입이 없다(2025년 말 출시된
# 신규 서비스라 프로바이더가 아직 못 따라감). 벡터 버킷/인덱스 2개(추천용, 구매도서
# RAG용)는 provider가 지원하는 버전이 나오면 여기 추가한다. 그때까지 outputs.tf의
# 관련 출력은 null이고, 02-runtime의 ai_service_iam이 이 값에 의존하는 부분도 같이 비어있다.

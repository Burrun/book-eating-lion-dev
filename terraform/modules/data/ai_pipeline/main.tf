# 실제 백엔드 코드(이벤트-메시징-명세.md)를 보면 신간 등록 이벤트는 S3 업로드 이벤트가
# 아니라 catalog-api가 직접 SQS로 발행하는 구조다. 그래서 S3 버킷 이벤트 알림은 여기서
# 만들지 않는다 - 앱이 SqsClient로 이 큐에 바로 SendMessage하면 된다.
resource "aws_sqs_queue" "ingest_dlq" {
  name                      = "lion-team3-${var.environment}-ai-ingest-dlq"
  message_retention_seconds = 1209600 # 14일 - 실패 메시지를 살펴볼 시간 확보
  sqs_managed_sse_enabled   = true    # 저장 데이터 암호화 - S3(storage 모듈)의 AES256 SSE와 동일 수준
}

resource "aws_sqs_queue" "ingest" {
  name                       = "lion-team3-${var.environment}-ai-ingest"
  visibility_timeout_seconds = 300 # 임베딩은 청크당 1회, 장편은 200~500청크라 넉넉히
  sqs_managed_sse_enabled    = true

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

# 구매 확정 이벤트 (order-api -> ai-api, 이벤트-메시징-명세.md §1). 발행측
# (SqsBookPurchasePublisher)/소비측(SqsPurchaseListener, 배치 10건) 둘 다 이미
# 동작 중인데 큐 자체가 없었다 - 큐 이름은 명세서에 명시된 그대로 사용.
resource "aws_sqs_queue" "purchase_dlq" {
  name                      = "lion-team3-${var.environment}-book-purchase-dlq"
  message_retention_seconds = 1209600 # 14일
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "purchase" {
  name                       = "lion-team3-${var.environment}-book-purchase-queue"
  visibility_timeout_seconds = 60 # 멱등 확인 -> 저장 -> 캐시 갱신, 배치 10건이라도 임베딩보단 훨씬 가벼움
  sqs_managed_sse_enabled    = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.purchase_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "purchase_dlq" {
  queue_url = aws_sqs_queue.purchase_dlq.id

  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.purchase.arn]
  })
}

# ── S3 Vectors — 아직 못 만듦 ──────────────────────────────────────
# hashicorp/aws provider 5.100.0 기준 s3vectors_* 리소스 타입이 없다(2025년 말 출시된
# 신규 서비스라 프로바이더가 아직 못 따라감). 벡터 버킷/인덱스 2개(추천용, 구매도서
# RAG용)는 provider가 지원하는 버전이 나오면 여기 추가한다. 그때까지 outputs.tf의
# 관련 출력은 null이고, 02-runtime의 ai_service_iam이 이 값에 의존하는 부분도 같이 비어있다.

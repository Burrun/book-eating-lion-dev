package com.bookeatinglion.book.port;

/**
 * 신간(EPUB 포함) 등록 이벤트 발행 포트. book-ingest-queue(SQS)로 나가며 ai-service 의
 * SqsIngestListener 가 이 큐를 읽어 EPUB을 벡터로 인제스트한다. 구현체는 apps:catalog-api 에
 * 둔다 — AWS SDK 의존을 도메인 모듈(modules:book)에 들이지 않기 위해서다
 * (order 모듈의 BookPurchasePublisher/SqsBookPurchasePublisher 와 같은 이유).
 */
public interface BookIngestPublisher {
    boolean publish(Long bookId, String title, String category, String epubS3Key);
}

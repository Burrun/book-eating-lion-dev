package com.bookeatinglion.order.event;

/**
 * 구매 확정 이벤트 발행 포트. book-purchase-queue(SQS)로 나가며 ai-service 가 검색 권한
 * 적재에 쓴다. 구현체는 apps:order-api 에 둔다 — AWS SDK 의존을 도메인 모듈(modules:order)에
 * 들이지 않기 위해서다.
 */
public interface BookPurchasePublisher {
    void publish(String memberId, Long bookId);
}

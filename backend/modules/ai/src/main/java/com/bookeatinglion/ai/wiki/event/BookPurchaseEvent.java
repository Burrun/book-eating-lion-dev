package com.bookeatinglion.ai.wiki.event;

// order-service 가 SQS 로 발행하는 구매 이벤트.
public record BookPurchaseEvent(String memberId, Long bookId) {}

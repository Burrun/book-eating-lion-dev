package com.bookeatinglion.ai.wiki.port;

// SQS 구매 이벤트 수신 포트. 구현체는 apps:ai-api 에 둔다.
public interface BookPurchaseEventPort {
    // SQS 폴링 시작. 구현체가 리스너 스레드를 관리한다.
    void startListening();
}

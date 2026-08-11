package com.bookeatinglion.order.payment.client;

/**
 * 레거시(book-eating-lion-bata) KakaoPayService 의 approve/cancel 모양을 그대로 따르되, 실제
 * 카카오페이 API 를 RestTemplate 으로 호출하던 부분은 뺐다 — 이 프로젝트엔 시크릿 키가 없고
 * 우리 주문 흐름은 레거시처럼 프론트 리다이렉트를 거치는 2단계(ready → approve)가 아니라
 * POST /api/orders 한 번에 승인까지 끝나므로 ready() 도 필요 없다.
 *
 * MockKakaoPayClient 가 유일한 구현체다. 나중에 실제 연동으로 바꿀 때는 이 인터페이스만
 * 유지한 채 RestTemplate/시크릿키 기반 구현체로 교체하면 PaymentService 는 손댈 필요가 없다.
 */
public interface KakaoPayClient {

    KakaoPayApproval approve(Long orderId, int amount);

    void cancel(String tid, int amount);

    record KakaoPayApproval(String tid, String approvalNumber) {}
}

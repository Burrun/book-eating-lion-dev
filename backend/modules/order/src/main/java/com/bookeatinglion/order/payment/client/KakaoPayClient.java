package com.bookeatinglion.order.payment.client;

/**
 * 카카오페이 실 Open API(https://open-api.kakaopay.com)와 통신한다. RealKakaoPayClient 가
 * 유일한 구현체다(레거시 book-eating-lion-bata KakaoPayService 의 ready/approve/cancel 모양을
 * 그대로 계승).
 *
 * ready() 는 tid 만 내주고 결제를 확정하지 않는다 — 사용자가 카카오 결제 페이지에서 실제로
 * 결제를 완료해야 pg_token 이 발급되고, 그 pg_token 을 들고 approve() 를 호출해야 비로소
 * 돈이 오간다. 이 간극 때문에 PaymentService/OrderService 가 2단계로 나뉜다.
 */
public interface KakaoPayClient {

    KakaoReadyResult ready(Long orderId, Long memberId, String itemName, int amount);

    KakaoApproveResult approve(Long orderId, Long memberId, String tid, String pgToken);

    void cancel(String tid, int amount);

    record KakaoReadyResult(String tid, String nextRedirectPcUrl) {}

    record KakaoApproveResult(String approvalNumber) {}
}

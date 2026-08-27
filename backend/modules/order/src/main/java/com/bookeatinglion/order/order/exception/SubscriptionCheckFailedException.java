package com.bookeatinglion.order.order.exception;

/**
 * 구독 중복 여부를 member-service 에 물어보지 못했을 때.
 *
 * <p>🔴 "구독 없음"으로 가정하고 결제를 받지 않는다. 틀렸을 경우의 결과가 비대칭이다 —
 * 막았는데 사실 구독이 없었으면 사용자가 잠시 후 다시 시도하면 되지만, 통과시켰는데 사실
 * 구독 중이었으면 돈이 나간 뒤에야 알게 되고 환불 경로가 없다.
 * ({@code ChatWebSocketHandler#onlineAgents} 가 Redis 장애 시 "상담사 0명"으로 보는 것과 같은 판단)
 */
public class SubscriptionCheckFailedException extends OrderDomainException {

    public SubscriptionCheckFailedException(String memberId, Throwable cause) {
        super(OrderErrorCode.SUBSCRIPTION_CHECK_FAILED, "구독 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요: " + memberId, cause);
    }
}

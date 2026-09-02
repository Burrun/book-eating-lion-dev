package com.bookeatinglion.order.order.exception;

/**
 * 이미 구독 중인데 구독권을 또 결제하려 할 때. <b>돈이 나가기 전에</b> 던져야 의미가 있다 —
 * 결제 후에 알게 되면 환불이 필요한데 그 경로가 없다(OrderService 참고).
 */
public class AlreadySubscribedException extends OrderDomainException {

    public AlreadySubscribedException(String memberId) {
        super(OrderErrorCode.ALREADY_SUBSCRIBED, "이미 이용 중인 구독이 있습니다: " + memberId);
    }
}

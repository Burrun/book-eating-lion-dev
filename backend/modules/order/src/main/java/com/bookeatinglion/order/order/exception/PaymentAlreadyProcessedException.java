package com.bookeatinglion.order.order.exception;

public class PaymentAlreadyProcessedException extends OrderDomainException {

    public PaymentAlreadyProcessedException(Long orderId) {
        super(OrderErrorCode.PAYMENT_ALREADY_PROCESSED, "이미 처리된 주문입니다: " + orderId);
    }
}

package com.bookeatinglion.order.order.exception;

public class UnauthorizedOrderAccessException extends OrderDomainException {

    public UnauthorizedOrderAccessException(Long orderId) {
        super(OrderErrorCode.UNAUTHORIZED_ORDER_ACCESS, "본인의 주문이 아닙니다: " + orderId);
    }
}

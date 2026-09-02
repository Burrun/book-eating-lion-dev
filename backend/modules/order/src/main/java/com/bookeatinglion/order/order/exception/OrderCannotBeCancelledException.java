package com.bookeatinglion.order.order.exception;

public class OrderCannotBeCancelledException extends OrderDomainException {

    public OrderCannotBeCancelledException(Long orderId) {
        super(OrderErrorCode.ORDER_CANNOT_BE_CANCELLED, "PAID 상태가 아니어서 취소할 수 없습니다: " + orderId);
    }
}

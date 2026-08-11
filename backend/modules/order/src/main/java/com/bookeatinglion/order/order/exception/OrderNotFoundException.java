package com.bookeatinglion.order.order.exception;

public class OrderNotFoundException extends OrderDomainException {

    public OrderNotFoundException(Long orderId) {
        super(OrderErrorCode.ORDER_NOT_FOUND, "존재하지 않는 주문입니다: " + orderId);
    }
}

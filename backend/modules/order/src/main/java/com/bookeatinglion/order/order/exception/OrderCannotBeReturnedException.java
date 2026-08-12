package com.bookeatinglion.order.order.exception;

public class OrderCannotBeReturnedException extends OrderDomainException {

    public OrderCannotBeReturnedException(Long orderId) {
        super(OrderErrorCode.ORDER_CANNOT_BE_RETURNED, "PAID 상태가 아니어서 반품 신청할 수 없습니다: " + orderId);
    }
}

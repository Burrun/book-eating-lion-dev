package com.bookeatinglion.order.order.exception;

public class OrderCannotBeRefundedException extends OrderDomainException {

    public OrderCannotBeRefundedException(Long orderId) {
        super(OrderErrorCode.ORDER_CANNOT_BE_REFUNDED, "RETURN_REQUESTED 상태가 아니어서 환불 처리할 수 없습니다: " + orderId);
    }
}

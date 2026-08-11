package com.bookeatinglion.order.order.exception;

/** 이미 사용됨 / 만료됨 / 최소 주문 금액 미달 등, 쿠폰은 존재하고 본인 것이지만 이번 주문에는 쓸 수 없는 경우. */
public class InvalidCouponException extends OrderDomainException {

    public InvalidCouponException(String message) {
        super(OrderErrorCode.INVALID_COUPON, message);
    }
}

package com.bookeatinglion.order.order.exception;

public class UnauthorizedCouponAccessException extends OrderDomainException {

    public UnauthorizedCouponAccessException(Long memberCouponId) {
        super(OrderErrorCode.UNAUTHORIZED_COUPON_ACCESS, "본인의 쿠폰이 아닙니다: " + memberCouponId);
    }
}

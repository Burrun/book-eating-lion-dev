package com.bookeatinglion.order.coupon.exception;

public class CouponExpiredException extends CouponDomainException {

    public CouponExpiredException(String couponCode) {
        super(CouponErrorCode.COUPON_EXPIRED, "만료된 쿠폰입니다: " + couponCode);
    }
}

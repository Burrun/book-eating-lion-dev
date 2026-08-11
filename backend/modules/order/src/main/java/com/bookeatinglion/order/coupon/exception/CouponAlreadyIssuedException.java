package com.bookeatinglion.order.coupon.exception;

public class CouponAlreadyIssuedException extends CouponDomainException {

    public CouponAlreadyIssuedException(String couponCode) {
        super(CouponErrorCode.COUPON_ALREADY_ISSUED, "이미 보유한 쿠폰입니다: " + couponCode);
    }
}

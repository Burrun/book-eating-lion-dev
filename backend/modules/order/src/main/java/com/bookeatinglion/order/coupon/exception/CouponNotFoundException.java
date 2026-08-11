package com.bookeatinglion.order.coupon.exception;

public class CouponNotFoundException extends CouponDomainException {

    public CouponNotFoundException(String couponCode) {
        super(CouponErrorCode.COUPON_NOT_FOUND, "존재하지 않는 쿠폰 코드입니다: " + couponCode);
    }
}

package com.bookeatinglion.order.coupon.exception;

public class CouponCodeDuplicatedException extends CouponDomainException {

    public CouponCodeDuplicatedException(String couponCode) {
        super(CouponErrorCode.COUPON_CODE_DUPLICATED, "이미 존재하는 쿠폰 코드입니다: " + couponCode);
    }
}

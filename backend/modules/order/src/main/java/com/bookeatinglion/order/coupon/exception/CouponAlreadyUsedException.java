package com.bookeatinglion.order.coupon.exception;

public class CouponAlreadyUsedException extends CouponDomainException {

    public CouponAlreadyUsedException(Long memberCouponId) {
        super(CouponErrorCode.COUPON_ALREADY_USED, "이미 사용한 쿠폰입니다: " + memberCouponId);
    }
}

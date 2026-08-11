package com.bookeatinglion.order.coupon.exception;

public abstract class CouponDomainException extends RuntimeException {

    private final CouponErrorCode errorCode;

    protected CouponDomainException(CouponErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CouponErrorCode getErrorCode() {
        return errorCode;
    }
}

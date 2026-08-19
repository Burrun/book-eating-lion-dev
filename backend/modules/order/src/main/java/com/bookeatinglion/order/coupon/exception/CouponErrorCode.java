package com.bookeatinglion.order.coupon.exception;

import org.springframework.http.HttpStatus;

public enum CouponErrorCode {
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT),
    COUPON_ALREADY_USED(HttpStatus.CONFLICT),
    COUPON_CODE_DUPLICATED(HttpStatus.CONFLICT),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    CouponErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

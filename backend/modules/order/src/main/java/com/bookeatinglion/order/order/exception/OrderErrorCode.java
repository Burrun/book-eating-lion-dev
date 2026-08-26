package com.bookeatinglion.order.order.exception;

import org.springframework.http.HttpStatus;

public enum OrderErrorCode {
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    UNAUTHORIZED_ORDER_ACCESS(HttpStatus.FORBIDDEN),
    OUT_OF_STOCK(HttpStatus.BAD_REQUEST),
    BOOK_PRICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    ORDER_COUPON_NOT_FOUND(HttpStatus.NOT_FOUND),
    UNAUTHORIZED_COUPON_ACCESS(HttpStatus.FORBIDDEN),
    INVALID_COUPON(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    ORDER_CANNOT_BE_CANCELLED(HttpStatus.CONFLICT),
    PAYMENT_DECLINED(HttpStatus.PAYMENT_REQUIRED),
    CARD_RESTORE_FAILED(HttpStatus.CONFLICT),
    LOCK_ACQUISITION_FAILED(HttpStatus.SERVICE_UNAVAILABLE),
    KAKAOPAY_API_ERROR(HttpStatus.SERVICE_UNAVAILABLE),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT),
    ORDER_CANNOT_BE_RETURNED(HttpStatus.CONFLICT),
    ORDER_CANNOT_BE_REFUNDED(HttpStatus.CONFLICT),
    ALREADY_SUBSCRIBED(HttpStatus.CONFLICT),
    SUBSCRIPTION_CHECK_FAILED(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    OrderErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

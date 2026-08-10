package com.bookeatinglion.order.delivery.exception;

import org.springframework.http.HttpStatus;

public enum DeliveryErrorCode {
    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND),
    UNAUTHORIZED_DELIVERY_ACCESS(HttpStatus.FORBIDDEN),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    DeliveryErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

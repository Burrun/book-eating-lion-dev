package com.bookeatinglion.order.cart.exception;

import org.springframework.http.HttpStatus;

public enum CartErrorCode {
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND),
    UNAUTHORIZED_CART_ACCESS(HttpStatus.FORBIDDEN),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    CartErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

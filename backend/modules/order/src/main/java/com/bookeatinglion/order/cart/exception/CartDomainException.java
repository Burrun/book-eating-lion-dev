package com.bookeatinglion.order.cart.exception;

public abstract class CartDomainException extends RuntimeException {

    private final CartErrorCode errorCode;

    protected CartDomainException(CartErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CartErrorCode getErrorCode() {
        return errorCode;
    }
}

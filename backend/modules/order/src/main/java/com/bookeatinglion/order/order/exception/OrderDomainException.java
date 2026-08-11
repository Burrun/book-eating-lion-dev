package com.bookeatinglion.order.order.exception;

public abstract class OrderDomainException extends RuntimeException {

    private final OrderErrorCode errorCode;

    protected OrderDomainException(OrderErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public OrderErrorCode getErrorCode() {
        return errorCode;
    }
}

package com.bookeatinglion.order.delivery.exception;

public abstract class DeliveryDomainException extends RuntimeException {

    private final DeliveryErrorCode errorCode;

    protected DeliveryDomainException(DeliveryErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DeliveryErrorCode getErrorCode() {
        return errorCode;
    }
}

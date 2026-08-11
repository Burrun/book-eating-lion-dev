package com.bookeatinglion.order.order.exception;

public class InvalidOrderRequestException extends OrderDomainException {

    public InvalidOrderRequestException(String message) {
        super(OrderErrorCode.INVALID_REQUEST, message);
    }
}

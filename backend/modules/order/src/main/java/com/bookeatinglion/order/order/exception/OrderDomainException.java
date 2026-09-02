package com.bookeatinglion.order.order.exception;

public abstract class OrderDomainException extends RuntimeException {

    private final OrderErrorCode errorCode;

    protected OrderDomainException(OrderErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** 외부 호출 실패를 도메인 예외로 감쌀 때. 원인을 버리면 로그에 스택이 안 남는다. */
    protected OrderDomainException(OrderErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public OrderErrorCode getErrorCode() {
        return errorCode;
    }
}

package com.bookeatinglion.member.subscription.exception;

public abstract class SubscriptionDomainException extends RuntimeException {

    private final SubscriptionErrorCode errorCode;

    protected SubscriptionDomainException(SubscriptionErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SubscriptionErrorCode getErrorCode() {
        return errorCode;
    }
}

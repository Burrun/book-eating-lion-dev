package com.bookeatinglion.member.subscription.exception;

import org.springframework.http.HttpStatus;

public enum SubscriptionErrorCode {
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    ALREADY_SUBSCRIBED(HttpStatus.CONFLICT),
    INVALID_SUBSCRIPTION_STATE(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    SubscriptionErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

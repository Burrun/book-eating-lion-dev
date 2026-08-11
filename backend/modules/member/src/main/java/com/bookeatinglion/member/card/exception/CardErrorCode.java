package com.bookeatinglion.member.card.exception;

import org.springframework.http.HttpStatus;

public enum CardErrorCode {
    CARD_NOT_FOUND(HttpStatus.NOT_FOUND),
    UNAUTHORIZED_CARD_ACCESS(HttpStatus.FORBIDDEN),
    INVALID_CARD_STATUS_CHANGE(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    CardErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

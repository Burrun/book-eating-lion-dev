package com.bookeatinglion.member.address.exception;

import org.springframework.http.HttpStatus;

public enum AddressErrorCode {
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    AddressErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

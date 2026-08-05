package com.bookeatinglion.admin.exception;

import org.springframework.http.HttpStatus;

public enum AdminErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    AdminErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

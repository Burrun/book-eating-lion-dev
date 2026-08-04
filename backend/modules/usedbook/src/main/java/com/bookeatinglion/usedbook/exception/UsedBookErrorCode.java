package com.bookeatinglion.usedbook.exception;

import org.springframework.http.HttpStatus;

public enum UsedBookErrorCode {

    USED_BOOK_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_ISBN(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    S3_UPLOAD_ERROR(HttpStatus.BAD_GATEWAY);

    private final HttpStatus status;

    UsedBookErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

package com.bookeatinglion.book.exception;

import org.springframework.http.HttpStatus;

public enum BookErrorCode {
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND),
    CATALOG_CONFLICT(HttpStatus.CONFLICT),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND),
    REVIEW_ACCESS_DENIED(HttpStatus.FORBIDDEN),
    REVIEW_PERMISSION_REQUIRED(HttpStatus.FORBIDDEN),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    BookErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

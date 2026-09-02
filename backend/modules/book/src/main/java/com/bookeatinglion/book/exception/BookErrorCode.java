package com.bookeatinglion.book.exception;

import org.springframework.http.HttpStatus;

public enum BookErrorCode {
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND),
    CATALOG_CONFLICT(HttpStatus.CONFLICT),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND),
    REVIEW_ACCESS_DENIED(HttpStatus.FORBIDDEN),
    REVIEW_PERMISSION_REQUIRED(HttpStatus.FORBIDDEN),
    RESTOCK_ALERT_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESTOCK_ALERT_CONFLICT(HttpStatus.CONFLICT),
    INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND),
    INQUIRY_ACCESS_DENIED(HttpStatus.FORBIDDEN),
    FAQ_NOT_FOUND(HttpStatus.NOT_FOUND),
    SUBSCRIPTION_BANNER_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_RECOMMENDATION_REACTION(HttpStatus.BAD_REQUEST),
    EBOOK_ACCESS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    EBOOK_OWNERSHIP_REQUIRED(HttpStatus.FORBIDDEN),
    HIGHLIGHT_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    BookErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

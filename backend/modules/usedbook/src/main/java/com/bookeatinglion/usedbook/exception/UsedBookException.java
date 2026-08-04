package com.bookeatinglion.usedbook.exception;

public abstract class UsedBookException extends RuntimeException {

    private final UsedBookErrorCode errorCode;

    protected UsedBookException(UsedBookErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected UsedBookException(UsedBookErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public UsedBookErrorCode getErrorCode() {
        return errorCode;
    }
}

package com.bookeatinglion.member.exception;

public abstract class MemberException extends RuntimeException {

    private final String code;

    protected MemberException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected MemberException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

package com.bookeatinglion.member.exception;

public class CognitoAuthException extends MemberException {

    public CognitoAuthException(String code, String message) {
        super(code, message);
    }

    public CognitoAuthException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}

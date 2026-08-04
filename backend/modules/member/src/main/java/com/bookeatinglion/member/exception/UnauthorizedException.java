package com.bookeatinglion.member.exception;

public class UnauthorizedException extends MemberException {

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }
}

package com.bookeatinglion.member.exception;

public class DuplicateEmailException extends MemberException {

    public DuplicateEmailException(String email) {
        super("DUPLICATE_EMAIL", "이미 가입된 이메일입니다: " + email);
    }
}

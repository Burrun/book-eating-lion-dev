package com.bookeatinglion.member.exception;

public class MemberNotFoundException extends MemberException {

    public MemberNotFoundException(String memberId) {
        super("MEMBER_NOT_FOUND", "해당 회원을 찾을 수 없습니다: " + memberId);
    }
}

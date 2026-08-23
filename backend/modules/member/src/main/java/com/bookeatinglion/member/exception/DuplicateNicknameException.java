package com.bookeatinglion.member.exception;

public class DuplicateNicknameException extends MemberException {

    public DuplicateNicknameException(String nickname) {
        super("DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다: " + nickname);
    }
}

package com.bookeatinglion.member.dto;

import com.bookeatinglion.member.domain.Member;

public record SignupResponse(String memberId, String email, String name) {
    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getId(), member.getEmail(), member.getName());
    }
}

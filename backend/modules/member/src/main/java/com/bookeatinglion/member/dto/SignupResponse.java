package com.bookeatinglion.member.dto;

import com.bookeatinglion.member.domain.Member;

/**
 * memberId 는 DB PK 가 아니라 Cognito sub 다. 다른 서비스(order, catalog, ai)가 회원을 sub 로
 * 식별하므로 외부로 나가는 식별자도 sub 여야 한다 — PK 를 내보내면 프론트가 서비스마다 다른
 * 값을 들고 다니게 된다.
 */
public record SignupResponse(
        String memberId,
        String email,
        String name
) {
    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getCognitoSub(), member.getEmail(), member.getName());
    }
}

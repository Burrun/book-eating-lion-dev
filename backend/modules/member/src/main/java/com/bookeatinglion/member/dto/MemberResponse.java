package com.bookeatinglion.member.dto;

import com.bookeatinglion.member.domain.Gender;
import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.domain.Role;
import java.time.LocalDate;

/**
 * id 는 DB PK 가 아니라 Cognito sub 다. JWT 의 {@code sub} 클레임과 같은 값이며,
 * order/catalog/ai 가 memberId 로 저장하는 값과도 같다 — 프론트가 서비스 경계를 넘어
 * 같은 식별자를 쓸 수 있어야 한다.
 */
public record MemberResponse(
        String id, String email, String name, String phoneNumber, Gender gender, LocalDate birthDate, Role role) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getCognitoSub(),
                member.getEmail(),
                member.getName(),
                member.getPhoneNumber(),
                member.getGender(),
                member.getBirthDate(),
                member.getRole());
    }
}

package com.bookeatinglion.member.dto;

import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.domain.MemberGrade;

public record MemberGradeResponse(
        MemberGrade grade,
        int point
) {
    public static MemberGradeResponse from(Member member) {
        return new MemberGradeResponse(member.getGrade(), member.getPoint());
    }
}

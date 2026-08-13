package com.bookeatinglion.member.dto;

import com.bookeatinglion.member.domain.Gender;
import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.domain.Role;
import java.time.LocalDate;

public record MemberResponse(
        String id, String email, String name, String phoneNumber, Gender gender, LocalDate birthDate, Role role) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getPhoneNumber(),
                member.getGender(),
                member.getBirthDate(),
                member.getRole());
    }
}

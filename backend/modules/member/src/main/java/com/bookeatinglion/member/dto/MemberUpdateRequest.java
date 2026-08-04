package com.bookeatinglion.member.dto;

import com.bookeatinglion.member.domain.Gender;

import java.time.LocalDate;

public record MemberUpdateRequest(
        String name,
        String phoneNumber,
        Gender gender,
        LocalDate birthDate
) {
}

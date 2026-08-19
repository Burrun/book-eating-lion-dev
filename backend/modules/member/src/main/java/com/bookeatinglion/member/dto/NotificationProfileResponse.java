package com.bookeatinglion.member.dto;

import com.bookeatinglion.member.domain.Member;

public record NotificationProfileResponse(String memberId, String email, String name) {
    public static NotificationProfileResponse from(Member member) {
        return new NotificationProfileResponse(member.getId(), member.getEmail(), member.getName());
    }
}

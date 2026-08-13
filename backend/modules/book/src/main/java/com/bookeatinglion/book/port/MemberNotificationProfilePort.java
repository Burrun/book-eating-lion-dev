package com.bookeatinglion.book.port;

public interface MemberNotificationProfilePort {
    NotificationProfile findByMemberId(String memberId);

    record NotificationProfile(String memberId, String email, String name) {}
}

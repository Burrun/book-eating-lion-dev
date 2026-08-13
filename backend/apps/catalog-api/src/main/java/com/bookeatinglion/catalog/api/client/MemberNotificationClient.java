package com.bookeatinglion.catalog.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "member-notification-profile", url = "${services.member.url}")
public interface MemberNotificationClient {
    @GetMapping("/internal/members/{memberId}/notification-profile")
    NotificationProfile findByMemberId(@PathVariable("memberId") String memberId);

    record NotificationProfile(String memberId, String email, String name) {}
}

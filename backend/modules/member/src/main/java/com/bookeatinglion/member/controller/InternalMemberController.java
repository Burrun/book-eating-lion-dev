package com.bookeatinglion.member.controller;

import com.bookeatinglion.member.dto.NotificationProfileResponse;
import com.bookeatinglion.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 클러스터 내부 서비스가 알림 수신 정보를 조회하는 API. Ingress 외부 노출 대상이 아니다. */
@RestController
@RequestMapping("/internal/members")
@RequiredArgsConstructor
public class InternalMemberController {

    private final MemberService memberService;

    @GetMapping("/{memberId}/notification-profile")
    public NotificationProfileResponse getNotificationProfile(@PathVariable String memberId) {
        return memberService.getNotificationProfile(memberId);
    }
}

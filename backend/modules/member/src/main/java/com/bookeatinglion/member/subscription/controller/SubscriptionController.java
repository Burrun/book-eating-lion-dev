package com.bookeatinglion.member.subscription.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import com.bookeatinglion.member.subscription.dto.SubscribeRequest;
import com.bookeatinglion.member.subscription.dto.SubscriptionResponse;
import com.bookeatinglion.member.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /** 구독 이력이 없으면 data: null 로 200 을 반환한다(reading-progress 와 동일 패턴). */
    @GetMapping
    public ApiResponse<SubscriptionResponse> getMySubscription() {
        String memberSub = SecurityUtils.currentMemberSub();
        return ApiResponse.success(subscriptionService.getMySubscription(memberSub));
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<SubscriptionResponse> subscribe(@Valid @RequestBody SubscribeRequest request) {
        String memberSub = SecurityUtils.currentMemberSub();
        return ApiResponse.success(subscriptionService.subscribe(memberSub, request.planType()));
    }

    @DeleteMapping
    public ApiResponse<SubscriptionResponse> cancel() {
        String memberSub = SecurityUtils.currentMemberSub();
        return ApiResponse.success(subscriptionService.cancel(memberSub));
    }
}

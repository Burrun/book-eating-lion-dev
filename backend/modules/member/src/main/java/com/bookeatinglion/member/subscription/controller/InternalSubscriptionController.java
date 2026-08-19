package com.bookeatinglion.member.subscription.controller;

import com.bookeatinglion.member.subscription.dto.SubscriptionStatusResponse;
import com.bookeatinglion.member.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /internal/** 은 서비스 간 전용이다(catalog-service 등이 eBook/RAG/웹툰 접근권을 검증할 때 참조할
 * 예정). 외부에는 노출하지 않는다 — Ingress 는 /api/** 만 라우팅하고 NetworkPolicy 가 클러스터 밖
 * 트래픽을 막는다.
 */
@RestController
@RequestMapping("/internal/members")
@RequiredArgsConstructor
public class InternalSubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/{memberId}/subscription-status")
    public SubscriptionStatusResponse getSubscriptionStatus(@PathVariable String memberId) {
        return subscriptionService.getSubscriptionStatus(memberId);
    }
}

package com.bookeatinglion.member.subscription.controller;

import com.bookeatinglion.member.subscription.dto.SubscribeRequest;
import com.bookeatinglion.member.subscription.dto.SubscriptionResponse;
import com.bookeatinglion.member.subscription.dto.SubscriptionStatusResponse;
import com.bookeatinglion.member.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * 결제 확정 후 order-service 가 구독을 만들 때 부른다.
     *
     * <p>공개 계약 {@code POST /api/members/me/subscription} 은 본인 JWT 로만 부를 수 있어
     * order-service 가 사용자를 대신해 부를 수 없다. 그래서 memberId 를 경로로 받는 내부용을
     * 따로 둔다 — 그만큼 이 경로는 클러스터 밖으로 나가면 안 된다(위 클래스 주석 참고).
     *
     * <p>멱등하다. 이미 ACTIVE 면 그 구독을 그대로 돌려주고 201 이 아니라 200 이다 —
     * 호출측이 재시도해도 안전해야 한다(SubscriptionService#activateByPayment 참고).
     */
    @PostMapping("/{memberId}/subscription")
    public SubscriptionResponse activateSubscription(
            @PathVariable String memberId, @Valid @RequestBody SubscribeRequest request) {
        return subscriptionService.activateByPayment(memberId, request.planType());
    }
}

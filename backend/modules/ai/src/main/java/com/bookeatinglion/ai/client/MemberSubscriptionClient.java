package com.bookeatinglion.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * member-service 구독 상태 조회(§7.6 예외 — ai-service 의 유일한 outbound 통신). 먹이기
 * EXP 2배 판정에만 쓰인다. member-v1.yaml 의 /internal/members/{memberId}/subscription-status
 * 계약을 그대로 쓴다.
 *
 * <p>구독 여부만 필요해서 응답의 status/expiresAt 은 무시한다 —
 * {@link JsonIgnoreProperties}(ignoreUnknown = true)로 계약이 필드를 더 줘도 깨지지 않는다.
 */
@FeignClient(
        name = "member-subscription",
        url = "${services.member.url}",
        fallback = MemberSubscriptionClientFallback.class)
public interface MemberSubscriptionClient {

    @GetMapping("/internal/members/{memberId}/subscription-status")
    SubscriptionStatus getSubscriptionStatus(@PathVariable("memberId") String memberId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SubscriptionStatus(String memberId, boolean subscribed) {}
}

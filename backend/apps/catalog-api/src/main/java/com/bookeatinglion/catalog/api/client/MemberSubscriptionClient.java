package com.bookeatinglion.catalog.api.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * member-service 의 /internal/members/{memberId}/subscription-status 계약. eBook 소유권
 * 검증(구독 회원은 개별 구매 여부와 무관하게 열람 가능)에 쓰인다. ai-service의
 * MemberSubscriptionClient와 같은 계약을 별도로 호출한다(모듈 간 의존 금지, §7.2).
 *
 * 구독 여부만 필요해서 응답의 status/expiresAt은 무시한다 —
 * {@link JsonIgnoreProperties}(ignoreUnknown = true)로 계약이 필드를 더 줘도 깨지지 않는다.
 */
@FeignClient(name = "member-subscription", url = "${services.member.url}", fallback = MemberSubscriptionFallback.class)
public interface MemberSubscriptionClient {

    @GetMapping("/internal/members/{memberId}/subscription-status")
    SubscriptionStatus getSubscriptionStatus(@PathVariable("memberId") String memberId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SubscriptionStatus(String memberId, boolean subscribed) {}
}

package com.bookeatinglion.catalog.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * member-service 무응답을 fail-open(구독으로 간주해 eBook 전체 열람을 허용)하지 않고
 * fail-closed(비구독으로 강등)한다. 근거 없이 접근을 열어주면 결제 우회로 이어지는 사고가
 * 된다 — order-service의 CardClientFallback, ai의 MemberSubscriptionClientFallback과 같은
 * 원칙이다. 구매 확정 기반 열람(review_permissions)은 이 실패로 막히지 않는다.
 */
@Slf4j
@Component
public class MemberSubscriptionFallback implements MemberSubscriptionClient {

    @Override
    public SubscriptionStatus getSubscriptionStatus(String memberId) {
        log.warn("member-service 구독 상태 조회 실패 — 비구독으로 안전 강등한다. memberId={}", memberId);
        return new SubscriptionStatus(memberId, false);
    }
}

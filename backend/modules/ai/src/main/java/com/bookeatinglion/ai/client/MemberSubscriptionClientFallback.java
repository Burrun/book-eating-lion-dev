package com.bookeatinglion.ai.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * member-service 무응답을 fail-open(구독으로 간주해 2배 지급)하지 않고 fail-closed(1배로
 * 강등)한다. 근거 없이 배율을 올려주면 조용히 과다 지급되는 사고가 된다 — order-service의
 * CardClientFallback과 같은 원칙이다. 먹이기 자체는 이 실패로 막히지 않는다(EXP 배율만
 * 영향받는다).
 */
@Slf4j
@Component
public class MemberSubscriptionClientFallback implements MemberSubscriptionClient {

    @Override
    public SubscriptionStatus getSubscriptionStatus(String memberId) {
        log.warn("member-service 구독 상태 조회 실패 — 1배(비구독)로 안전 강등한다. memberId={}", memberId);
        return new SubscriptionStatus(memberId, false);
    }
}

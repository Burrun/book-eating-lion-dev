package com.bookeatinglion.member.subscription.service;

import com.bookeatinglion.member.subscription.domain.PlanType;
import com.bookeatinglion.member.subscription.domain.Subscription;
import com.bookeatinglion.member.subscription.domain.SubscriptionStatus;
import com.bookeatinglion.member.subscription.dto.SubscriptionResponse;
import com.bookeatinglion.member.subscription.dto.SubscriptionStatusResponse;
import com.bookeatinglion.member.subscription.exception.AlreadySubscribedException;
import com.bookeatinglion.member.subscription.exception.SubscriptionNotFoundException;
import com.bookeatinglion.member.subscription.repository.SubscriptionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    /** 결제 미연동 스코프: 본인 호출로 즉시 활성화한다. 이미 ACTIVE 구독이 있으면 거부. */
    @Transactional
    public SubscriptionResponse subscribe(String memberSub, PlanType planType) {
        subscriptionRepository
                .findByMemberSubAndStatus(memberSub, SubscriptionStatus.ACTIVE)
                .ifPresent(s -> {
                    throw new AlreadySubscribedException(memberSub);
                });

        Subscription subscription = subscriptionRepository.save(Subscription.start(memberSub, planType));
        return SubscriptionResponse.from(subscription);
    }

    /**
     * 결제가 확정된 뒤 order-service 가 부른다.
     *
     * <p>🔴 {@link #subscribe}와 달리 이미 ACTIVE 여도 예외를 던지지 않고 그 구독을 그대로
     * 돌려준다. 이 시점엔 이미 돈이 빠져나갔다 — 여기서 409 를 던지면 결제만 되고 구독은
     * 없는 상태로 굳는다. 호출측이 재시도해도 안전해야 하므로 멱등이어야 한다.
     *
     * <p>같은 이유로 "이미 구독 중인데 또 결제한" 경우의 환불/기간연장은 여기서 판단하지
     * 않는다. 그건 결제를 받기 전에 막아야 하는 문제다(프론트의 구독 CTA 가 ACTIVE 면
     * 결제로 보내지 않는다).
     */
    @Transactional
    public SubscriptionResponse activateByPayment(String memberSub, PlanType planType) {
        return subscriptionRepository
                .findByMemberSubAndStatus(memberSub, SubscriptionStatus.ACTIVE)
                .map(SubscriptionResponse::from)
                .orElseGet(() -> SubscriptionResponse.from(
                        subscriptionRepository.save(Subscription.start(memberSub, planType))));
    }

    /** 구독 이력이 없으면 null(호출측이 data: null 로 200 응답한다). */
    @Transactional
    public SubscriptionResponse getMySubscription(String memberSub) {
        Optional<Subscription> subscription =
                subscriptionRepository.findFirstByMemberSubOrderByCreatedAtDescIdDesc(memberSub);
        subscription.ifPresent(Subscription::expireIfDue);
        return subscription.map(SubscriptionResponse::from).orElse(null);
    }

    @Transactional
    public SubscriptionResponse cancel(String memberSub) {
        Subscription subscription = subscriptionRepository
                .findByMemberSubAndStatus(memberSub, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionNotFoundException(memberSub));
        subscription.cancel();
        return SubscriptionResponse.from(subscription);
    }

    /** /internal/members/{memberId}/subscription-status. 다른 서비스(catalog 등)가 참조한다. */
    @Transactional
    public SubscriptionStatusResponse getSubscriptionStatus(String memberId) {
        Optional<Subscription> subscription =
                subscriptionRepository.findFirstByMemberSubOrderByCreatedAtDescIdDesc(memberId);
        subscription.ifPresent(Subscription::expireIfDue);
        return subscription
                .map(s -> SubscriptionStatusResponse.from(memberId, s))
                .orElse(SubscriptionStatusResponse.none(memberId));
    }
}

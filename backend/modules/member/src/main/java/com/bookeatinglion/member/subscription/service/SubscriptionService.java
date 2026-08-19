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

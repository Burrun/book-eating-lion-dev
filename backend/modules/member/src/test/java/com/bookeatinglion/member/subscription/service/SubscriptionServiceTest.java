package com.bookeatinglion.member.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bookeatinglion.member.subscription.domain.PlanType;
import com.bookeatinglion.member.subscription.domain.Subscription;
import com.bookeatinglion.member.subscription.domain.SubscriptionStatus;
import com.bookeatinglion.member.subscription.dto.SubscriptionResponse;
import com.bookeatinglion.member.subscription.dto.SubscriptionStatusResponse;
import com.bookeatinglion.member.subscription.exception.AlreadySubscribedException;
import com.bookeatinglion.member.subscription.exception.SubscriptionNotFoundException;
import com.bookeatinglion.member.subscription.repository.SubscriptionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final String MEMBER_SUB = "member-sub-1";

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Subscription subscription(Long id, PlanType planType, LocalDateTime expiresAt) {
        Subscription subscription = Subscription.start(MEMBER_SUB, planType);
        ReflectionTestUtils.setField(subscription, "id", id);
        ReflectionTestUtils.setField(subscription, "expiresAt", expiresAt);
        return subscription;
    }

    @Test
    void 구독을_시작하면_ACTIVE_상태로_생성된다() {
        when(subscriptionRepository.findByMemberSubAndStatus(MEMBER_SUB, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionResponse response = subscriptionService.subscribe(MEMBER_SUB, PlanType.MONTHLY);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.planType()).isEqualTo(PlanType.MONTHLY);
    }

    @Test
    void 이미_구독중이면_예외를_던진다() {
        when(subscriptionRepository.findByMemberSubAndStatus(MEMBER_SUB, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(
                        subscription(1L, PlanType.MONTHLY, LocalDateTime.now().plusMonths(1))));

        assertThatThrownBy(() -> subscriptionService.subscribe(MEMBER_SUB, PlanType.YEARLY))
                .isInstanceOf(AlreadySubscribedException.class);
    }

    @Test
    void 구독_이력이_없으면_null을_반환한다() {
        when(subscriptionRepository.findFirstByMemberSubOrderByCreatedAtDescIdDesc(MEMBER_SUB))
                .thenReturn(Optional.empty());

        SubscriptionResponse response = subscriptionService.getMySubscription(MEMBER_SUB);

        assertThat(response).isNull();
    }

    @Test
    void 활성_구독을_조회한다() {
        when(subscriptionRepository.findFirstByMemberSubOrderByCreatedAtDescIdDesc(MEMBER_SUB))
                .thenReturn(Optional.of(
                        subscription(1L, PlanType.YEARLY, LocalDateTime.now().plusYears(1))));

        SubscriptionResponse response = subscriptionService.getMySubscription(MEMBER_SUB);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.planType()).isEqualTo(PlanType.YEARLY);
    }

    @Test
    void 만료기한이_지난_구독_조회시_EXPIRED로_전환된다() {
        when(subscriptionRepository.findFirstByMemberSubOrderByCreatedAtDescIdDesc(MEMBER_SUB))
                .thenReturn(Optional.of(
                        subscription(1L, PlanType.MONTHLY, LocalDateTime.now().minusDays(1))));

        SubscriptionResponse response = subscriptionService.getMySubscription(MEMBER_SUB);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void 구독을_해지한다() {
        Subscription active =
                subscription(1L, PlanType.MONTHLY, LocalDateTime.now().plusMonths(1));
        when(subscriptionRepository.findByMemberSubAndStatus(MEMBER_SUB, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        SubscriptionResponse response = subscriptionService.cancel(MEMBER_SUB);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(response.cancelledAt()).isNotNull();
    }

    @Test
    void 활성_구독이_없으면_해지시_예외를_던진다() {
        when(subscriptionRepository.findByMemberSubAndStatus(MEMBER_SUB, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.cancel(MEMBER_SUB))
                .isInstanceOf(SubscriptionNotFoundException.class);
    }

    @Test
    void 내부_구독상태조회_이력없으면_subscribed_false를_반환한다() {
        when(subscriptionRepository.findFirstByMemberSubOrderByCreatedAtDescIdDesc(MEMBER_SUB))
                .thenReturn(Optional.empty());

        SubscriptionStatusResponse response = subscriptionService.getSubscriptionStatus(MEMBER_SUB);

        assertThat(response.subscribed()).isFalse();
        assertThat(response.memberId()).isEqualTo(MEMBER_SUB);
    }

    @Test
    void 내부_구독상태조회_활성구독이면_subscribed_true를_반환한다() {
        when(subscriptionRepository.findFirstByMemberSubOrderByCreatedAtDescIdDesc(MEMBER_SUB))
                .thenReturn(Optional.of(
                        subscription(1L, PlanType.MONTHLY, LocalDateTime.now().plusMonths(1))));

        SubscriptionStatusResponse response = subscriptionService.getSubscriptionStatus(MEMBER_SUB);

        assertThat(response.subscribed()).isTrue();
    }
}

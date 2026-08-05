package com.bookeatinglion.admin.repository;

import com.bookeatinglion.admin.AdminModuleTestApplication;
import com.bookeatinglion.admin.domain.Subscription;
import com.bookeatinglion.admin.domain.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = AdminModuleTestApplication.class)
class SubscriptionRepositoryTest {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @BeforeEach
    void setUp() {
        subscriptionRepository.save(subscription(1L, SubscriptionStatus.ACTIVE));
        subscriptionRepository.save(subscription(2L, SubscriptionStatus.ACTIVE));
        subscriptionRepository.save(subscription(3L, SubscriptionStatus.CANCELLED));
    }

    private Subscription subscription(Long memberId, SubscriptionStatus status) {
        return Subscription.builder()
                .memberId(memberId)
                .planName("월간 구독")
                .monthlyPrice(9900)
                .subscriptionStatus(status)
                .build();
    }

    @Test
    void 활성_구독자_수를_카운트한다() {
        long count = subscriptionRepository.countBySubscriptionStatus(SubscriptionStatus.ACTIVE);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void 해지_구독자_수를_카운트한다() {
        long count = subscriptionRepository.countBySubscriptionStatus(SubscriptionStatus.CANCELLED);

        assertThat(count).isEqualTo(1);
    }
}

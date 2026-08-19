package com.bookeatinglion.member.subscription.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookeatinglion.member.MemberModuleTestApplication;
import com.bookeatinglion.member.subscription.domain.PlanType;
import com.bookeatinglion.member.subscription.domain.Subscription;
import com.bookeatinglion.member.subscription.domain.SubscriptionStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = MemberModuleTestApplication.class)
class SubscriptionRepositoryTest {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Test
    void 최신_구독_이력을_조회한다() {
        subscriptionRepository.save(Subscription.start("member-sub-1", PlanType.MONTHLY));
        Subscription latest = subscriptionRepository.save(Subscription.start("member-sub-1", PlanType.YEARLY));

        Optional<Subscription> found =
                subscriptionRepository.findFirstByMemberSubOrderByCreatedAtDescIdDesc("member-sub-1");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(latest.getId());
    }

    @Test
    void 활성_구독을_상태로_조회한다() {
        subscriptionRepository.save(Subscription.start("member-sub-1", PlanType.MONTHLY));

        Optional<Subscription> found =
                subscriptionRepository.findByMemberSubAndStatus("member-sub-1", SubscriptionStatus.ACTIVE);

        assertThat(found).isPresent();
    }

    @Test
    void 존재하지_않는_회원의_구독은_조회되지_않는다() {
        Optional<Subscription> found =
                subscriptionRepository.findFirstByMemberSubOrderByCreatedAtDescIdDesc("no-such-member");

        assertThat(found).isEmpty();
    }
}

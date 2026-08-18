package com.bookeatinglion.member.subscription.repository;

import com.bookeatinglion.member.subscription.domain.Subscription;
import com.bookeatinglion.member.subscription.domain.SubscriptionStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    // createdAt 만으로는 동시(같은 밀리초) 생성 시 순서가 흔들릴 수 있어 id 를 2차 정렬 기준으로 둔다.
    Optional<Subscription> findFirstByMemberSubOrderByCreatedAtDescIdDesc(String memberSub);

    Optional<Subscription> findByMemberSubAndStatus(String memberSub, SubscriptionStatus status);
}

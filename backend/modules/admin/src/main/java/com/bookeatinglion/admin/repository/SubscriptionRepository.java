package com.bookeatinglion.admin.repository;

import com.bookeatinglion.admin.domain.Subscription;
import com.bookeatinglion.admin.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    long countBySubscriptionStatus(SubscriptionStatus subscriptionStatus);
}

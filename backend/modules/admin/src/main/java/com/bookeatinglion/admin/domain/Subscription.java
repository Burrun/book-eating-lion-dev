package com.bookeatinglion.admin.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String planName;

    @Column(nullable = false)
    private long monthlyPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus subscriptionStatus;

    private LocalDate nextDeliveryDate;

    private LocalDateTime cancelledAt;

    @Builder
    public Subscription(Long memberId, String planName, long monthlyPrice,
                         SubscriptionStatus subscriptionStatus, LocalDate nextDeliveryDate,
                         LocalDateTime cancelledAt) {
        this.memberId = memberId;
        this.planName = planName;
        this.monthlyPrice = monthlyPrice;
        this.subscriptionStatus = subscriptionStatus != null ? subscriptionStatus : SubscriptionStatus.ACTIVE;
        this.nextDeliveryDate = nextDeliveryDate;
        this.cancelledAt = cancelledAt;
    }
}

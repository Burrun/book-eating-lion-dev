package com.bookeatinglion.member.subscription.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import com.bookeatinglion.member.subscription.exception.InvalidSubscriptionStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * member_db.member_subscriptions. 정기구독(프리미엄 멤버십) 상태만 소유한다 — 결제 상세(금액/
 * 수단/승인번호)는 order_db 의 payments 관심사라 여기 두지 않는다. 결제 완료 시 order 가
 * 이 구독을 활성화하는 내부 연동은 이후 과제다.
 *
 * order_db.subscriptions(정기배송 구독박스)와는 별개 개념이다.
 */
@Entity
@Table(name = "member_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long id;

    @Column(name = "member_sub", nullable = false)
    private String memberSub;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    private PlanType planType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    private Subscription(String memberSub, PlanType planType, LocalDateTime startedAt, LocalDateTime expiresAt) {
        this.memberSub = memberSub;
        this.planType = planType;
        this.status = SubscriptionStatus.ACTIVE;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    /** 결제 없이 즉시 활성화한다(이번 스코프). MONTHLY 는 +1개월, YEARLY 는 +1년. */
    public static Subscription start(String memberSub, PlanType planType) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = planType == PlanType.YEARLY ? now.plusYears(1) : now.plusMonths(1);
        return new Subscription(memberSub, planType, now, expiresAt);
    }

    /** 본인 해지. ACTIVE 상태가 아니면(이미 해지/만료) 상태 전이를 허용하지 않는다. */
    public void cancel() {
        if (this.status != SubscriptionStatus.ACTIVE) {
            throw new InvalidSubscriptionStateException(id, this.status);
        }
        this.status = SubscriptionStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    /** 만료 기한이 지난 ACTIVE 구독을 EXPIRED 로 전환한다. 전환이 일어났으면 true. */
    public boolean expireIfDue() {
        if (this.status == SubscriptionStatus.ACTIVE && !this.expiresAt.isAfter(LocalDateTime.now())) {
            this.status = SubscriptionStatus.EXPIRED;
            return true;
        }
        return false;
    }

    public boolean isOwnedBy(String memberSub) {
        return this.memberSub.equals(memberSub);
    }
}

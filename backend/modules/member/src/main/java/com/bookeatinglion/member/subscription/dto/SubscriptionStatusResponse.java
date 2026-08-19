package com.bookeatinglion.member.subscription.dto;

import com.bookeatinglion.member.subscription.domain.Subscription;
import com.bookeatinglion.member.subscription.domain.SubscriptionStatus;
import java.time.LocalDateTime;

/** /internal/members/{memberId}/subscription-status. catalog 등 다른 서비스가 참조하는 조회 전용 응답. */
public record SubscriptionStatusResponse(
        String memberId, boolean subscribed, SubscriptionStatus status, LocalDateTime expiresAt) {

    public static SubscriptionStatusResponse from(String memberId, Subscription subscription) {
        return new SubscriptionStatusResponse(
                memberId,
                subscription.getStatus() == SubscriptionStatus.ACTIVE,
                subscription.getStatus(),
                subscription.getExpiresAt());
    }

    public static SubscriptionStatusResponse none(String memberId) {
        return new SubscriptionStatusResponse(memberId, false, null, null);
    }
}

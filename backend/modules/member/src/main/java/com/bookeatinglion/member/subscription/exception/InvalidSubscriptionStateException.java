package com.bookeatinglion.member.subscription.exception;

import com.bookeatinglion.member.subscription.domain.SubscriptionStatus;

public class InvalidSubscriptionStateException extends SubscriptionDomainException {

    public InvalidSubscriptionStateException(Long subscriptionId, SubscriptionStatus currentStatus) {
        super(
                SubscriptionErrorCode.INVALID_SUBSCRIPTION_STATE,
                "이미 " + currentStatus + " 상태인 구독은 해지할 수 없습니다: " + subscriptionId);
    }
}

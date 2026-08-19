package com.bookeatinglion.member.subscription.exception;

public class SubscriptionNotFoundException extends SubscriptionDomainException {

    public SubscriptionNotFoundException(String memberSub) {
        super(SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND, "활성 구독이 없습니다: " + memberSub);
    }
}

package com.bookeatinglion.member.subscription.exception;

public class AlreadySubscribedException extends SubscriptionDomainException {

    public AlreadySubscribedException(String memberSub) {
        super(SubscriptionErrorCode.ALREADY_SUBSCRIBED, "이미 구독 중입니다: " + memberSub);
    }
}

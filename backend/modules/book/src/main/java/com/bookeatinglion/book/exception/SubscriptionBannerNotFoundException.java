package com.bookeatinglion.book.exception;

public class SubscriptionBannerNotFoundException extends RuntimeException {
    public SubscriptionBannerNotFoundException(Long bannerId) {
        super("Subscription banner not found: id=" + bannerId);
    }
}

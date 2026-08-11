package com.bookeatinglion.member.card.dto;

/** order-service 의 CardClient.CardOperationResult 와 1:1. */
public record CardOperationResult(boolean approved, String message) {

    public static CardOperationResult approve() {
        return new CardOperationResult(true, null);
    }

    public static CardOperationResult declined(String message) {
        return new CardOperationResult(false, message);
    }
}

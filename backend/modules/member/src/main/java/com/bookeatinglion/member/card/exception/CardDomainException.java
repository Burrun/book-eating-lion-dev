package com.bookeatinglion.member.card.exception;

public abstract class CardDomainException extends RuntimeException {

    private final CardErrorCode errorCode;

    protected CardDomainException(CardErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CardErrorCode getErrorCode() {
        return errorCode;
    }
}

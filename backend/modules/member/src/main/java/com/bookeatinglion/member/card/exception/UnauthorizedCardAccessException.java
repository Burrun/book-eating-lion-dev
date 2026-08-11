package com.bookeatinglion.member.card.exception;

public class UnauthorizedCardAccessException extends CardDomainException {

    public UnauthorizedCardAccessException(Long cardId) {
        super(CardErrorCode.UNAUTHORIZED_CARD_ACCESS, "본인의 카드가 아닙니다: " + cardId);
    }
}

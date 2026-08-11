package com.bookeatinglion.member.card.exception;

public class CardNotFoundException extends CardDomainException {

    public CardNotFoundException(Long cardId) {
        super(CardErrorCode.CARD_NOT_FOUND, "존재하지 않는 카드입니다: " + cardId);
    }
}

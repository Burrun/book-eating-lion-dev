package com.bookeatinglion.member.card.exception;

public class InvalidCardStatusChangeException extends CardDomainException {

    public InvalidCardStatusChangeException(Long cardId) {
        super(CardErrorCode.INVALID_CARD_STATUS_CHANGE, "CLOSED 카드는 상태를 변경할 수 없습니다: " + cardId);
    }
}

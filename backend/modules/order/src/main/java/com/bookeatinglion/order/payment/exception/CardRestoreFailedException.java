package com.bookeatinglion.order.payment.exception;

import com.bookeatinglion.order.order.exception.OrderDomainException;
import com.bookeatinglion.order.order.exception.OrderErrorCode;

/** 취소는 단일 트랜잭션이라 이 예외가 재고/쿠폰 복구까지 전부 롤백시킨다(부분 취소 방지). */
public class CardRestoreFailedException extends OrderDomainException {

    public CardRestoreFailedException(Long cardId) {
        super(OrderErrorCode.CARD_RESTORE_FAILED, "가상카드 한도 복구에 실패했습니다: cardId=" + cardId);
    }
}

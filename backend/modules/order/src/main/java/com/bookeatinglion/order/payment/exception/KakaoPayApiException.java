package com.bookeatinglion.order.payment.exception;

import com.bookeatinglion.order.order.exception.OrderDomainException;
import com.bookeatinglion.order.order.exception.OrderErrorCode;

/**
 * 카카오페이 Open API 호출(ready/approve/cancel) 실패. ready/approve 실패는 주문 생성·승인
 * 트랜잭션을 롤백시키고, cancel 실패는 취소 트랜잭션 전체(재고·쿠폰 복구 포함)를 롤백시킨다
 * (CardRestoreFailedException 과 같은 원칙).
 */
public class KakaoPayApiException extends OrderDomainException {

    public KakaoPayApiException(String message) {
        super(OrderErrorCode.KAKAOPAY_API_ERROR, message);
    }
}

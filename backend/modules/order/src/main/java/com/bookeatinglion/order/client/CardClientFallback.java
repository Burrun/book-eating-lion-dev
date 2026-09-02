package com.bookeatinglion.order.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * member-service 무응답을 fail-open(승인 처리) 하지 않고 fail-closed(거절)로 degrade 한다.
 * deduct 실패는 결제 거절로, restore 실패는 취소 트랜잭션 롤백으로 이어진다(PaymentService).
 * 둘 다 "돈이 걸린 조작을 확인 없이 성공시키지 않는다"는 같은 원칙이다.
 */
@Slf4j
@Component
public class CardClientFallback implements CardClient {

    @Override
    public CardOperationResult deduct(Long cardId, CardOperationRequest request) {
        log.warn("member-service 카드 한도 차감 실패 — 결제를 거절합니다. cardId={}, amount={}", cardId, request.amount());
        return new CardOperationResult(false, "member-service 응답 없음");
    }

    @Override
    public CardOperationResult restore(Long cardId, CardOperationRequest request) {
        log.warn("member-service 카드 한도 복구 실패 — 취소를 롤백합니다. cardId={}, amount={}", cardId, request.amount());
        return new CardOperationResult(false, "member-service 응답 없음");
    }
}

package com.bookeatinglion.order.payment.service;

import com.bookeatinglion.order.client.CardClient;
import com.bookeatinglion.order.client.CardClient.CardOperationRequest;
import com.bookeatinglion.order.client.CardClient.CardOperationResult;
import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.payment.client.KakaoPayClient;
import com.bookeatinglion.order.payment.client.KakaoPayClient.KakaoApproveResult;
import com.bookeatinglion.order.payment.client.KakaoPayClient.KakaoReadyResult;
import com.bookeatinglion.order.payment.domain.Payment;
import com.bookeatinglion.order.payment.domain.PaymentMethod;
import com.bookeatinglion.order.payment.exception.CardRestoreFailedException;
import com.bookeatinglion.order.payment.exception.PaymentDeclinedException;
import com.bookeatinglion.order.payment.repository.PaymentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CARD 는 1단계(단일 호출) — member-service 가상카드 한도를 동기 차감/복구한다(CardClient).
 * KAKAOPAY 는 2단계(ready → approve) — RealKakaoPayClient 가 카카오페이 실 API 와 통신한다.
 * 결제 승인 실패는 PaymentDeclinedException 으로 표현해 주문 생성 트랜잭션 전체를 롤백시킨다 —
 * DECLINED 행은 절대 저장되지 않는다(Payment 클래스 주석 참고).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CardClient cardClient;
    private final KakaoPayClient kakaoPayClient;

    @Transactional
    public Payment approveCard(Order order, Long cardId, int amount) {
        CardOperationResult result = cardClient.deduct(cardId, new CardOperationRequest(amount));
        if (!result.approved()) {
            throw new PaymentDeclinedException(result.message());
        }

        String approvalNumber =
                "AP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Payment payment = Payment.approved(
                order, cardId, PaymentMethod.VIRTUAL_CARD, amount, approvalNumber, null, newIdempotencyKey());
        return paymentRepository.save(payment);
    }

    /** 카카오페이 1단계. 아직 결제는 확정되지 않는다 — Payment 는 READY 로 저장된다. */
    @Transactional
    public KakaoReadyOutcome readyKakao(Order order, String memberId, int amount) {
        String itemName = "도서 주문 #" + order.getId();
        KakaoReadyResult result = kakaoPayClient.ready(order.getId(), memberId, itemName, amount);

        Payment payment = paymentRepository.save(Payment.ready(order, amount, result.tid(), newIdempotencyKey()));
        return new KakaoReadyOutcome(payment, result.nextRedirectPcUrl());
    }

    /**
     * 카카오페이 2단계. 호출 시점엔 이미 재고 재검증을 마친 뒤여야 한다(OrderService) —
     * 재고가 없으면 이 메서드 자체를 호출하지 않아 카카오에 승인 요청을 보내지 않는다.
     */
    @Transactional
    public void approveKakao(Payment payment, Long orderId, String memberId, String pgToken) {
        KakaoApproveResult result = kakaoPayClient.approve(orderId, memberId, payment.getPgTid(), pgToken);
        payment.approveKakao(result.approvalNumber());
    }

    /**
     * 취소는 단일 트랜잭션이다 — 카드 한도 복구/카카오 취소가 실패하면 예외가 재고·쿠폰 복구까지
     * 전부 롤백시킨다(CardRestoreFailedException, KakaoPayApiException).
     */
    @Transactional
    public void cancel(Payment payment) {
        restoreFunds(payment);
        payment.cancel();
    }

    /**
     * 반품 승인 후 환불이다. 자금 복구 방식(카드 한도 복구/카카오 취소 API)은 cancel() 과
     * 동일하다 — 카카오페이 실 API 는 취소와 환불을 같은 cancel 엔드포인트로 처리한다.
     * cancel() 과 다른 건 Payment 가 최종적으로 REFUNDED 로 남는다는 것뿐이다.
     */
    @Transactional
    public void refund(Payment payment) {
        restoreFunds(payment);
        payment.refund();
    }

    private void restoreFunds(Payment payment) {
        if (payment.getPaymentMethod() == PaymentMethod.VIRTUAL_CARD) {
            CardOperationResult result =
                    cardClient.restore(payment.getCardId(), new CardOperationRequest(payment.getAmount()));
            if (!result.approved()) {
                throw new CardRestoreFailedException(payment.getCardId());
            }
        } else {
            kakaoPayClient.cancel(payment.getPgTid(), payment.getAmount());
        }
    }

    private String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    public record KakaoReadyOutcome(Payment payment, String redirectUrl) {}
}

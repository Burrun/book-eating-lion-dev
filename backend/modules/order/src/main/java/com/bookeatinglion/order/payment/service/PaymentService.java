package com.bookeatinglion.order.payment.service;

import com.bookeatinglion.order.client.CardClient;
import com.bookeatinglion.order.client.CardClient.CardOperationRequest;
import com.bookeatinglion.order.client.CardClient.CardOperationResult;
import com.bookeatinglion.order.order.domain.Order;
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
 * CARD 는 member-service 의 가상카드 한도를 동기 차감/복구하고(CardClient), KAKAOPAY 는 실제 PG
 * 연동 전이라 이 서비스가 승인번호/거래ID 를 목(mock) 생성한다. 두 경우 모두 결제 승인 실패를
 * PaymentDeclinedException 으로 표현해 주문 생성 트랜잭션 전체를 롤백시킨다 — DECLINED 행은
 * 절대 저장되지 않는다(Payment 클래스 주석 참고).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CardClient cardClient;

    @Transactional
    public Payment approve(Order order, PaymentMethod paymentMethod, Long cardId, int amount) {
        String approvalNumber = null;
        String pgTid = null;

        if (paymentMethod == PaymentMethod.CARD) {
            CardOperationResult result = cardClient.deduct(cardId, new CardOperationRequest(amount));
            if (!result.approved()) {
                throw new PaymentDeclinedException(result.message());
            }
            approvalNumber = mockCode("AP");
        } else {
            pgTid = mockCode("KAKAO");
        }

        Payment payment = new Payment(
                order,
                cardId,
                paymentMethod,
                amount,
                approvalNumber,
                pgTid,
                UUID.randomUUID().toString());
        return paymentRepository.save(payment);
    }

    /** 취소는 단일 트랜잭션이다 — 카드 한도 복구가 실패하면 이 예외가 재고/쿠폰 복구까지 전부 롤백시킨다. */
    @Transactional
    public void cancel(Payment payment) {
        if (payment.getPaymentMethod() == PaymentMethod.CARD) {
            CardOperationResult result =
                    cardClient.restore(payment.getCardId(), new CardOperationRequest(payment.getAmount()));
            if (!result.approved()) {
                throw new CardRestoreFailedException(payment.getCardId());
            }
        }
        payment.cancel();
    }

    private String mockCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

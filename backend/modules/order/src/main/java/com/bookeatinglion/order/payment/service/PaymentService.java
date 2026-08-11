package com.bookeatinglion.order.payment.service;

import com.bookeatinglion.order.client.CardClient;
import com.bookeatinglion.order.client.CardClient.CardOperationRequest;
import com.bookeatinglion.order.client.CardClient.CardOperationResult;
import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.payment.client.KakaoPayClient;
import com.bookeatinglion.order.payment.client.KakaoPayClient.KakaoPayApproval;
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
 * CARD 는 member-service 의 가상카드 한도를 동기 차감/복구하고(CardClient), KAKAOPAY 는
 * KakaoPayClient(현재는 MockKakaoPayClient)가 tid/승인번호를 발급/취소한다. 결제 승인 실패는
 * PaymentDeclinedException 으로 표현해 주문 생성 트랜잭션 전체를 롤백시킨다 — DECLINED 행은
 * 절대 저장되지 않는다(Payment 클래스 주석 참고).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CardClient cardClient;
    private final KakaoPayClient kakaoPayClient;

    @Transactional
    public Payment approve(Order order, PaymentMethod paymentMethod, Long cardId, int amount) {
        String approvalNumber = null;
        String pgTid = null;

        if (paymentMethod == PaymentMethod.CARD) {
            CardOperationResult result = cardClient.deduct(cardId, new CardOperationRequest(amount));
            if (!result.approved()) {
                throw new PaymentDeclinedException(result.message());
            }
            approvalNumber =
                    "AP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } else {
            KakaoPayApproval approval = kakaoPayClient.approve(order.getId(), amount);
            pgTid = approval.tid();
            approvalNumber = approval.approvalNumber();
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

    /**
     * 취소는 단일 트랜잭션이다 — 카드 한도 복구가 실패하면 CardRestoreFailedException 이 재고·쿠폰
     * 복구까지 전부 롤백시킨다. KakaoPayClient.cancel() 은 현재 목(mock)이라 실패 경로가 없다 —
     * 실제 PG 연동으로 바뀌면 그때 카드처럼 승인/거절 결과를 반환하도록 인터페이스를 넓히면 된다.
     */
    @Transactional
    public void cancel(Payment payment) {
        if (payment.getPaymentMethod() == PaymentMethod.CARD) {
            CardOperationResult result =
                    cardClient.restore(payment.getCardId(), new CardOperationRequest(payment.getAmount()));
            if (!result.approved()) {
                throw new CardRestoreFailedException(payment.getCardId());
            }
        } else {
            kakaoPayClient.cancel(payment.getPgTid(), payment.getAmount());
        }
        payment.cancel();
    }
}

package com.bookeatinglion.order.payment.domain;

import com.bookeatinglion.order.order.domain.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * order_db.payments. approvalNumber 는 CARD/카카오 승인 후, pgTid 는 KAKAOPAY 의 ready
 * 단계부터 채워진다 — 서로 다른 결제망의 서로 다른 식별자를 억지로 하나로 합치지 않는다
 * (PaymentService 참고).
 *
 * READY 상태는 원래 스키마(APPROVED/DECLINED/CANCELLED)에 없었다. 카카오페이는 ready 와
 * approve 가 별도 HTTP 요청으로 나뉘고, approve 요청은 tid 를 다시 보내주지 않으므로
 * (orderId + pgToken 뿐) 서버가 ready 시점에 tid 를 어딘가 들고 있어야 한다 — 그 자리가
 * READY 상태의 이 행이다. DECLINED 는 여전히 이 테이블에 남지 않는다 — 거절되면
 * PaymentDeclinedException 이 트랜잭션 전체를 롤백시켜 행 자체가 저장되지 않는다.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "card_id")
    private Long cardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "approval_number")
    private String approvalNumber;

    @Column(name = "pg_tid")
    private String pgTid;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    private Payment(
            Order order,
            Long cardId,
            PaymentMethod paymentMethod,
            int amount,
            PaymentStatus paymentStatus,
            String approvalNumber,
            String pgTid,
            String idempotencyKey) {
        this.order = order;
        this.cardId = cardId;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.paymentStatus = paymentStatus;
        this.approvalNumber = approvalNumber;
        this.pgTid = pgTid;
        this.idempotencyKey = idempotencyKey;
    }

    /** CARD 1단계 승인, 또는 (미래에) 즉시 승인되는 다른 결제수단용. */
    public static Payment approved(
            Order order,
            Long cardId,
            PaymentMethod paymentMethod,
            int amount,
            String approvalNumber,
            String pgTid,
            String idempotencyKey) {
        return new Payment(
                order, cardId, paymentMethod, amount, PaymentStatus.APPROVED, approvalNumber, pgTid, idempotencyKey);
    }

    /** 카카오페이 ready 단계. approvalNumber 는 approve 가 성공해야 채워진다. */
    public static Payment ready(Order order, int amount, String pgTid, String idempotencyKey) {
        return new Payment(
                order, null, PaymentMethod.KAKAO_PAY, amount, PaymentStatus.READY, null, pgTid, idempotencyKey);
    }

    /** 카카오페이 approve 성공. READY 상태에서만 호출할 수 있다. */
    public void approveKakao(String approvalNumber) {
        if (this.paymentStatus != PaymentStatus.READY) {
            throw new IllegalStateException("READY 상태에서만 승인할 수 있습니다: " + id);
        }
        this.approvalNumber = approvalNumber;
        this.paymentStatus = PaymentStatus.APPROVED;
    }

    public void cancel() {
        if (this.paymentStatus != PaymentStatus.APPROVED) {
            throw new IllegalStateException("APPROVED 상태에서만 CANCELLED 로 전환할 수 있습니다: " + id);
        }
        this.paymentStatus = PaymentStatus.CANCELLED;
    }

    /** 반품 승인 후 환불. cancel() 과 전이 조건은 같지만 종단 상태를 CANCELLED 와 구분한다. */
    public void refund() {
        if (this.paymentStatus != PaymentStatus.APPROVED) {
            throw new IllegalStateException("APPROVED 상태에서만 REFUNDED 로 전환할 수 있습니다: " + id);
        }
        this.paymentStatus = PaymentStatus.REFUNDED;
    }
}

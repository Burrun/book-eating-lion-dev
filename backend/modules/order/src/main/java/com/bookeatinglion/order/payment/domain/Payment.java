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
 * order_db.payments. approvalNumber 는 CARD, pgTid 는 KAKAOPAY 에서만 채워진다 —
 * 서로 다른 결제망의 서로 다른 식별자를 억지로 하나로 합치지 않는다(PaymentService 참고).
 *
 * 이 서비스는 실제 PG 연동 전이라 승인번호/거래ID 는 order-service 자신이 목(mock) 생성한다.
 * DECLINED 상태는 이 테이블에 남지 않는다 — 거절되면 PaymentDeclinedException 이 주문 생성
 * 트랜잭션 전체를 롤백시켜 Payment 행 자체가 저장되지 않는다.
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

    public Payment(
            Order order,
            Long cardId,
            PaymentMethod paymentMethod,
            int amount,
            String approvalNumber,
            String pgTid,
            String idempotencyKey) {
        this.order = order;
        this.cardId = cardId;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.paymentStatus = PaymentStatus.APPROVED;
        this.approvalNumber = approvalNumber;
        this.pgTid = pgTid;
        this.idempotencyKey = idempotencyKey;
    }

    public void cancel() {
        if (this.paymentStatus != PaymentStatus.APPROVED) {
            throw new IllegalStateException("APPROVED 상태에서만 CANCELLED 로 전환할 수 있습니다: " + id);
        }
        this.paymentStatus = PaymentStatus.CANCELLED;
    }
}

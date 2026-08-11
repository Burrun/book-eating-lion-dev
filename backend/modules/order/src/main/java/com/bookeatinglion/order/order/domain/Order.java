package com.bookeatinglion.order.order.domain;

import com.bookeatinglion.order.order.exception.OrderCannotBeCancelledException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * order_db.orders. delivery 가 참조하던 기존 스텁(memberId, bookId 두 필드짜리)을 이 자리로
 * 옮기고 정식 필드로 재작성했다 — 아무도 실제 인스턴스를 만들지 않던 자리였다(생성자가 없었다).
 * 참조하던 곳은 DeliveryService 뿐이라 getMemberId() 시그니처만 유지하면 됐다.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus;

    public Order(
            Long memberId,
            String recipientName,
            String recipientPhone,
            String postalCode,
            String address,
            int totalAmount) {
        this.memberId = memberId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.postalCode = postalCode;
        this.address = address;
        this.totalAmount = totalAmount;
        this.orderStatus = OrderStatus.PENDING_PAYMENT;
    }

    /** 결제 승인 직후 OrderService 안에서만 호출된다 — 결제가 거절되면 이 메서드 자체가 호출되지 않는다. */
    public void markPaid() {
        if (this.orderStatus != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("PENDING_PAYMENT 상태에서만 PAID 로 전환할 수 있습니다: " + id);
        }
        this.orderStatus = OrderStatus.PAID;
    }

    public void cancel() {
        if (this.orderStatus != OrderStatus.PAID) {
            throw new OrderCannotBeCancelledException(id);
        }
        this.orderStatus = OrderStatus.CANCELLED;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }
}

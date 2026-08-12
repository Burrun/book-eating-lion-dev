package com.bookeatinglion.order.order.domain;

import com.bookeatinglion.order.order.exception.OrderCannotBeCancelledException;
import com.bookeatinglion.order.order.exception.OrderCannotBeRefundedException;
import com.bookeatinglion.order.order.exception.OrderCannotBeReturnedException;
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
 *
 * pendingMemberCouponId 도 원래 스키마엔 없던 컬럼이다. 카카오페이 2단계 흐름에서
 * memberCouponId 는 주문 생성(ready) 시점에만 클라이언트가 보내고, approve 요청에는
 * orderId 와 pgToken 뿐이다 — 그 사이 "이 주문이 어떤 쿠폰을 쓰려고 했는지"를 서버가
 * 기억할 곳이 필요해서 추가했다. 쿠폰은 approve 가 성공해야 실제로 사용확정(use)된다.
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

    @Column(name = "member_id", nullable = false, length = 255)
    private String memberId;

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

    @Column(name = "pending_member_coupon_id")
    private Long pendingMemberCouponId;

    @Column(name = "return_reason")
    private String returnReason;

    public Order(
            String memberId,
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

    /** 배송 완료 후의 반품/교환 신청 접수다. 사전 취소(cancel)와 달리 즉시 환불되지 않는다. */
    public void requestReturn(String reason) {
        if (this.orderStatus != OrderStatus.PAID) {
            throw new OrderCannotBeReturnedException(id);
        }
        this.orderStatus = OrderStatus.RETURN_REQUESTED;
        this.returnReason = reason;
    }

    /** 반품 신청된 주문의 환불 완료 처리다. 재고/쿠폰/결제 복구는 OrderService/PaymentService 가 맡는다. */
    public void completeRefund() {
        if (this.orderStatus != OrderStatus.RETURN_REQUESTED) {
            throw new OrderCannotBeRefundedException(id);
        }
        this.orderStatus = OrderStatus.REFUNDED;
    }

    /** 카카오페이 ready 단계에서, 나중에 approve 될 때 사용확정할 쿠폰을 기억해둔다. */
    public void reservePendingCoupon(Long memberCouponId) {
        this.pendingMemberCouponId = memberCouponId;
    }

    public void clearPendingCoupon() {
        this.pendingMemberCouponId = null;
    }

    public boolean isOwnedBy(String memberId) {
        return this.memberId.equals(memberId);
    }
}

package com.bookeatinglion.order.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * order_db.order_items. bookTitle/unitPrice 는 주문 시점 스냅샷이다 — 이후 catalog 에서
 * 책값이 바뀌어도 이미 만든 주문의 금액은 변하지 않는다. Order 는 단방향 @ManyToOne 이다
 * (MemberCoupon-Coupon 과 동일한 패턴 — Order 는 자신의 아이템 목록을 들고 있지 않는다).
 */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "book_title", nullable = false)
    private String bookTitle;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

    public OrderItem(Order order, Long bookId, String bookTitle, int quantity, int unitPrice) {
        this.order = order;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public int subtotal() {
        return unitPrice * quantity;
    }
}

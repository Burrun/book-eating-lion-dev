package com.bookeatinglion.order.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long bookId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus orderStatus;

    @Column(nullable = false)
    private long totalAmount;

    @Builder
    public Order(Long memberId, Long bookId, OrderStatus orderStatus, long totalAmount) {
        this.memberId = memberId;
        this.bookId = bookId;
        this.orderStatus = orderStatus != null ? orderStatus : OrderStatus.PENDING_PAYMENT;
        this.totalAmount = totalAmount;
    }
}

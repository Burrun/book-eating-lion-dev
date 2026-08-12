package com.bookeatinglion.order.delivery.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import com.bookeatinglion.order.delivery.exception.InvalidDeliveryStatusTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    private String courierCompany;

    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus deliveryStatus;

    @Builder
    public Delivery(Long orderId, String courierCompany, String trackingNumber, DeliveryStatus deliveryStatus) {
        this.orderId = orderId;
        this.courierCompany = courierCompany;
        this.trackingNumber = trackingNumber;
        this.deliveryStatus = deliveryStatus != null ? deliveryStatus : DeliveryStatus.PENDING;
    }

    /** PENDING → SHIPPED → IN_TRANSIT → DELIVERED 순서로 한 단계씩만 전이한다 — 건너뛰기/역행 모두 금지. */
    public void updateStatus(DeliveryStatus newStatus) {
        if (this.deliveryStatus.next() != newStatus) {
            throw new InvalidDeliveryStatusTransitionException(this.orderId, this.deliveryStatus, newStatus);
        }
        this.deliveryStatus = newStatus;
    }
}

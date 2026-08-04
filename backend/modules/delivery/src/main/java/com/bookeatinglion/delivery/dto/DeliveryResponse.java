package com.bookeatinglion.delivery.dto;

import com.bookeatinglion.delivery.domain.Delivery;
import com.bookeatinglion.delivery.domain.DeliveryStatus;

import java.time.LocalDateTime;

public record DeliveryResponse(
        Long id,
        Long orderId,
        String courierCompany,
        String trackingNumber,
        DeliveryStatus deliveryStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getCourierCompany(),
                delivery.getTrackingNumber(),
                delivery.getDeliveryStatus(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt()
        );
    }
}

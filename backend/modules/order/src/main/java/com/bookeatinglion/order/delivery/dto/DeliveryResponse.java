package com.bookeatinglion.order.delivery.dto;

import com.bookeatinglion.order.delivery.domain.Delivery;
import com.bookeatinglion.order.delivery.domain.DeliveryStatus;
import java.time.LocalDateTime;

public record DeliveryResponse(
        Long id,
        Long orderId,
        String courierCompany,
        String trackingNumber,
        DeliveryStatus deliveryStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getCourierCompany(),
                delivery.getTrackingNumber(),
                delivery.getDeliveryStatus(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt());
    }
}

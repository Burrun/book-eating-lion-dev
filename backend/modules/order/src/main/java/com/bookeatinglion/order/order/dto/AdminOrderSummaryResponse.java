package com.bookeatinglion.order.order.dto;

import com.bookeatinglion.order.delivery.domain.DeliveryStatus;
import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.order.domain.OrderStatus;

/**
 * 관리자용 주문 목록 조회 DTO. 배송 레코드는 결제 확정 시점에만 생성되므로
 * (OrderService.createDelivery), PENDING_PAYMENT 상태의 주문은 deliveryStatus 가 null 이다.
 */
public record AdminOrderSummaryResponse(
        Long orderId,
        String memberId,
        String recipientName,
        OrderStatus orderStatus,
        DeliveryStatus deliveryStatus,
        int totalAmount) {

    public static AdminOrderSummaryResponse of(Order order, DeliveryStatus deliveryStatus) {
        return new AdminOrderSummaryResponse(
                order.getId(),
                order.getMemberId(),
                order.getRecipientName(),
                order.getOrderStatus(),
                deliveryStatus,
                order.getTotalAmount());
    }
}

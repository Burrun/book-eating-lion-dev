package com.bookeatinglion.order.order.dto;

import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.order.domain.OrderItem;
import com.bookeatinglion.order.order.domain.OrderStatus;
import com.bookeatinglion.order.payment.domain.Payment;
import com.bookeatinglion.order.payment.dto.PaymentView;
import java.util.List;

public record OrderResponse(
        Long orderId,
        OrderStatus orderStatus,
        Recipient recipient,
        int totalAmount,
        List<OrderItemView> items,
        PaymentView payment,
        String nextRedirectUrl) {

    /** CARD 생성 완료, 상세조회, 취소, 카카오 승인 이후 — nextRedirectUrl 이 항상 null 인 모든 경우. */
    public static OrderResponse of(Order order, List<OrderItem> items, Payment payment) {
        return of(order, items, payment, null);
    }

    /** KAKAOPAY 를 막 ready 했을 때만 쓴다. */
    public static OrderResponse of(Order order, List<OrderItem> items, Payment payment, String nextRedirectUrl) {
        return new OrderResponse(
                order.getId(),
                order.getOrderStatus(),
                new Recipient(
                        order.getRecipientName(), order.getRecipientPhone(), order.getPostalCode(), order.getAddress()),
                order.getTotalAmount(),
                items.stream().map(OrderItemView::from).toList(),
                payment == null ? null : PaymentView.from(payment),
                nextRedirectUrl);
    }
}

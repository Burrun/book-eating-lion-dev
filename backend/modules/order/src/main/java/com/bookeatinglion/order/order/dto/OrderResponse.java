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
        PaymentView payment) {

    public static OrderResponse of(Order order, List<OrderItem> items, Payment payment) {
        return new OrderResponse(
                order.getId(),
                order.getOrderStatus(),
                new Recipient(
                        order.getRecipientName(), order.getRecipientPhone(), order.getPostalCode(), order.getAddress()),
                order.getTotalAmount(),
                items.stream().map(OrderItemView::from).toList(),
                payment == null ? null : PaymentView.from(payment));
    }
}

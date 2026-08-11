package com.bookeatinglion.order.order.dto;

import com.bookeatinglion.order.order.domain.OrderItem;

public record OrderItemView(Long orderItemId, Long bookId, String bookTitle, int quantity, int unitPrice) {

    public static OrderItemView from(OrderItem orderItem) {
        return new OrderItemView(
                orderItem.getId(),
                orderItem.getBookId(),
                orderItem.getBookTitle(),
                orderItem.getQuantity(),
                orderItem.getUnitPrice());
    }
}

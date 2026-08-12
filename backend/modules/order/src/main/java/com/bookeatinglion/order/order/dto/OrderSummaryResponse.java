package com.bookeatinglion.order.order.dto;

import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.order.domain.OrderStatus;

/** 목록 조회용 요약 DTO. 품목/결제 상세는 상세조회(OrderResponse)에서만 내려준다 — N+1 없이 페이징하기 위함이다. */
public record OrderSummaryResponse(Long orderId, OrderStatus orderStatus, int totalAmount) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getOrderStatus(), order.getTotalAmount());
    }
}

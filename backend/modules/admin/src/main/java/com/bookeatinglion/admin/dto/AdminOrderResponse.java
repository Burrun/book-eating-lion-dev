package com.bookeatinglion.admin.dto;

import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.order.domain.Order;
import com.bookeatinglion.order.domain.OrderStatus;

import java.time.LocalDateTime;

public record AdminOrderResponse(
        Long orderId,
        Long memberId,
        String memberEmail,
        String memberName,
        OrderStatus orderStatus,
        long totalAmount,
        LocalDateTime createdAt
) {
    public static AdminOrderResponse from(Order order, Member member) {
        return new AdminOrderResponse(
                order.getId(),
                order.getMemberId(),
                member != null ? member.getEmail() : null,
                member != null ? member.getName() : null,
                order.getOrderStatus(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}

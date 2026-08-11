package com.bookeatinglion.order.order.dto;

import com.bookeatinglion.order.payment.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateOrderRequest(
        @NotEmpty List<@Valid OrderItemRequest> items,
        Long memberCouponId,
        @NotNull @Valid Recipient recipient,
        @NotNull PaymentMethod paymentMethod,
        Long cardId) {}

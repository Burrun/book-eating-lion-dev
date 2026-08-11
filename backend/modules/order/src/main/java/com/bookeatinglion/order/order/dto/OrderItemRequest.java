package com.bookeatinglion.order.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(@NotNull Long bookId, @Min(1) int quantity) {}

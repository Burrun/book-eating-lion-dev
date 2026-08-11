package com.bookeatinglion.order.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(@NotNull Long bookId, @Min(1) int quantity) {}

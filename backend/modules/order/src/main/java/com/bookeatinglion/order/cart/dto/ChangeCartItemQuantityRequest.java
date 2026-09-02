package com.bookeatinglion.order.cart.dto;

import jakarta.validation.constraints.Min;

public record ChangeCartItemQuantityRequest(@Min(1) int quantity) {}

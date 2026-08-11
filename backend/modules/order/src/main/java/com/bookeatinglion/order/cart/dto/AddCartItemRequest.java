package com.bookeatinglion.order.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** quantity 는 계약상 선택값이다(default: 1) — 생략되면 컨트롤러가 1로 채운다. */
public record AddCartItemRequest(@NotNull Long bookId, @Min(1) Integer quantity) {}

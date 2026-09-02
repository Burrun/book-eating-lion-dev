package com.bookeatinglion.order.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record KakaoApproveRequest(@NotNull Long orderId, @NotBlank String pgToken) {}

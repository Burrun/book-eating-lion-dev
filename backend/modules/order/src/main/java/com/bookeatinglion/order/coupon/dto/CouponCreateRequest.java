package com.bookeatinglion.order.coupon.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CouponCreateRequest(
        @NotBlank @Size(max = 100) String couponCode,
        @NotBlank @Size(max = 255) String couponName,
        @Positive int discountAmount,
        @Min(0) int minimumOrderAmount,
        @NotNull @Future LocalDateTime expiresAt) {}

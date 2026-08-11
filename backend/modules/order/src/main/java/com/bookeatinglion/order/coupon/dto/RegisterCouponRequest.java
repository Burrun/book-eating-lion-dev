package com.bookeatinglion.order.coupon.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterCouponRequest(@NotBlank String code) {}

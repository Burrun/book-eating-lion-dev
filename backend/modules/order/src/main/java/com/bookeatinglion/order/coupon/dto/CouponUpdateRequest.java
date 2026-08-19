package com.bookeatinglion.order.coupon.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

// couponCode 는 발급 식별자라 수정 대상에서 제외한다.
// expiresAt 에 @Future 를 걸지 않는다 — 과거로 넣는 것이 쿠폰을 종료하는 유일한 방법이다
// (coupons 에는 active 컬럼이 없고 member_coupons FK가 ON DELETE RESTRICT라 삭제도 못 한다).
public record CouponUpdateRequest(
        @NotBlank @Size(max = 255) String couponName,
        @Positive int discountAmount,
        @Min(0) int minimumOrderAmount,
        @NotNull LocalDateTime expiresAt) {}

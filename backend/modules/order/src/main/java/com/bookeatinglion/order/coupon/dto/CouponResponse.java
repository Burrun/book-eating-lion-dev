package com.bookeatinglion.order.coupon.dto;

import com.bookeatinglion.order.coupon.domain.Coupon;
import java.time.LocalDateTime;

public record CouponResponse(
        Long couponId,
        String couponCode,
        String couponName,
        int discountAmount,
        int minimumOrderAmount,
        LocalDateTime expiresAt) {

    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCouponCode(),
                coupon.getCouponName(),
                coupon.getDiscountAmount(),
                coupon.getMinimumOrderAmount(),
                coupon.getExpiresAt());
    }
}

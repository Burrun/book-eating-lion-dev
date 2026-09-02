package com.bookeatinglion.order.coupon.dto;

import com.bookeatinglion.order.coupon.domain.MemberCoupon;
import java.time.LocalDateTime;

public record MemberCouponView(
        Long memberCouponId,
        Long couponId,
        String couponCode,
        String couponName,
        int discountAmount,
        int minimumOrderAmount,
        LocalDateTime expiresAt) {

    public static MemberCouponView from(MemberCoupon memberCoupon) {
        var coupon = memberCoupon.getCoupon();
        return new MemberCouponView(
                memberCoupon.getId(),
                coupon.getId(),
                coupon.getCouponCode(),
                coupon.getCouponName(),
                coupon.getDiscountAmount(),
                coupon.getMinimumOrderAmount(),
                coupon.getExpiresAt());
    }
}

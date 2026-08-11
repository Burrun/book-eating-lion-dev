package com.bookeatinglion.order.order.exception;

public class OrderCouponNotFoundException extends OrderDomainException {

    public OrderCouponNotFoundException(Long memberCouponId) {
        super(OrderErrorCode.ORDER_COUPON_NOT_FOUND, "존재하지 않는 보유 쿠폰입니다: " + memberCouponId);
    }
}

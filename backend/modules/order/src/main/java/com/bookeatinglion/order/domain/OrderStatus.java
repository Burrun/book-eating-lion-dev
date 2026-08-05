package com.bookeatinglion.order.domain;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PAYMENT_FAILED,
    PREPARING,
    SHIPPED,
    DELIVERED,
    CANCEL_REQUESTED,
    CANCELLED,
    EXCHANGE_REQUESTED,
    EXCHANGED,
    RETURN_REQUESTED,
    RETURNED,
    REFUND_REQUESTED,
    REFUNDED
}

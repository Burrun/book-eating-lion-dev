package com.bookeatinglion.order.payment.domain;

public enum PaymentStatus {
    /** 카카오페이 ready 는 끝났지만 아직 approve 되지 않았다 — 원래 스키마엔 없던 상태다. */
    READY,
    APPROVED,
    DECLINED,
    CANCELLED
}

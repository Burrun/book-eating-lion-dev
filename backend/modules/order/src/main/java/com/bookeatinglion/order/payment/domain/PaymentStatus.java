package com.bookeatinglion.order.payment.domain;

public enum PaymentStatus {
    /** 카카오페이 ready 는 끝났지만 아직 approve 되지 않았다 — 원래 스키마엔 없던 상태다. */
    READY,
    APPROVED,
    DECLINED,
    CANCELLED,
    /** 배송 완료 후 반품 신청이 승인되어 환불된 상태다. CANCELLED(사전 취소)와 구분한다. */
    REFUNDED
}

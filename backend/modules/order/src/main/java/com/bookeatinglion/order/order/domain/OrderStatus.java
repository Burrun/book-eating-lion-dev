package com.bookeatinglion.order.order.domain;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    CANCELLED,
    /** 반품/교환 신청이 접수되어 환불 처리를 기다리는 상태다. */
    RETURN_REQUESTED,
    /** 반품 신청 후 환불(카드 한도 복구 또는 카카오페이 취소)까지 완료된 종단 상태다. */
    REFUNDED
}

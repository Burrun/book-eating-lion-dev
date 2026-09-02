package com.bookeatinglion.order.delivery.domain;

public enum DeliveryStatus {
    PENDING,
    SHIPPED,
    IN_TRANSIT,
    DELIVERED;

    /** 진행 순서상 다음 상태. ordinal 이 아니라 이 매핑 자체가 순서의 근거다 — DELIVERED 는 종단 상태라 다음이 없다. */
    public DeliveryStatus next() {
        return switch (this) {
            case PENDING -> SHIPPED;
            case SHIPPED -> IN_TRANSIT;
            case IN_TRANSIT -> DELIVERED;
            case DELIVERED -> null;
        };
    }
}

package com.bookeatinglion.order.delivery.exception;

import com.bookeatinglion.order.delivery.domain.DeliveryStatus;

public class InvalidDeliveryStatusTransitionException extends DeliveryDomainException {

    public InvalidDeliveryStatusTransitionException(Long orderId, DeliveryStatus current, DeliveryStatus requested) {
        super(
                DeliveryErrorCode.INVALID_DELIVERY_STATUS_TRANSITION,
                "배송 상태를 전환할 수 없습니다: orderId=" + orderId + ", current=" + current + ", requested=" + requested);
    }
}

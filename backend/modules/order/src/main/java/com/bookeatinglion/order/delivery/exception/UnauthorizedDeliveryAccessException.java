package com.bookeatinglion.order.delivery.exception;

public class UnauthorizedDeliveryAccessException extends DeliveryDomainException {

    public UnauthorizedDeliveryAccessException(Long orderId) {
        super(
                DeliveryErrorCode.UNAUTHORIZED_DELIVERY_ACCESS,
                "Member is not allowed to access delivery info for order: orderId=" + orderId);
    }
}

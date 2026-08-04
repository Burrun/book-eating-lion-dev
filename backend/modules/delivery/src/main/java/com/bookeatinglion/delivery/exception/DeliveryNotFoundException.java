package com.bookeatinglion.delivery.exception;

public class DeliveryNotFoundException extends DeliveryDomainException {

    public DeliveryNotFoundException(Long orderId) {
        super(DeliveryErrorCode.DELIVERY_NOT_FOUND, "Delivery not found for order: orderId=" + orderId);
    }
}

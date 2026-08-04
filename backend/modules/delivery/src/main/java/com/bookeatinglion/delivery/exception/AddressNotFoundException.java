package com.bookeatinglion.delivery.exception;

public class AddressNotFoundException extends DeliveryDomainException {

    public AddressNotFoundException(Long addressId) {
        super(DeliveryErrorCode.ADDRESS_NOT_FOUND, "Address not found: id=" + addressId);
    }
}

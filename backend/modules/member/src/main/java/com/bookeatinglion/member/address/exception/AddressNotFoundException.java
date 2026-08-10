package com.bookeatinglion.member.address.exception;

public class AddressNotFoundException extends AddressDomainException {

    public AddressNotFoundException(Long addressId) {
        super(AddressErrorCode.ADDRESS_NOT_FOUND, "Address not found: id=" + addressId);
    }
}

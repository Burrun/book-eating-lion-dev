package com.bookeatinglion.member.address.exception;

public class AddressNotFoundException extends AddressDomainException {

    public AddressNotFoundException(Long addressId) {
        super(AddressErrorCode.ADDRESS_NOT_FOUND, "존재하지 않는 배송지입니다: " + addressId);
    }
}

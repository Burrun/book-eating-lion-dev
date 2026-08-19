package com.bookeatinglion.member.address.exception;

public class UnauthorizedAddressAccessException extends AddressDomainException {

    public UnauthorizedAddressAccessException(Long addressId) {
        super(AddressErrorCode.UNAUTHORIZED_ADDRESS_ACCESS, "본인의 배송지가 아닙니다: " + addressId);
    }
}

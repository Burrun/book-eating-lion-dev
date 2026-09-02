package com.bookeatinglion.member.address.exception;

public abstract class AddressDomainException extends RuntimeException {

    private final AddressErrorCode errorCode;

    protected AddressDomainException(AddressErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AddressErrorCode getErrorCode() {
        return errorCode;
    }
}

package com.bookeatinglion.member.address.dto;

import com.bookeatinglion.member.address.domain.Address;

import java.time.LocalDateTime;

public record AddressResponse(
        Long id,
        String recipientName,
        String phoneNumber,
        String zipcode,
        String address,
        String detailAddress,
        boolean isDefault,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getRecipientName(),
                address.getPhoneNumber(),
                address.getZipcode(),
                address.getAddress(),
                address.getDetailAddress(),
                address.isDefault(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}

package com.bookeatinglion.member.address.dto;

public record AddressUpdateRequest(
        String recipientName,
        String phoneNumber,
        String zipcode,
        String address,
        String detailAddress,
        Boolean isDefault) {}

package com.bookeatinglion.delivery.dto;

import jakarta.validation.constraints.NotBlank;

public record AddressCreateRequest(
        @NotBlank String recipientName,
        @NotBlank String phoneNumber,
        @NotBlank String zipcode,
        @NotBlank String address,
        String detailAddress,
        boolean isDefault
) {
}

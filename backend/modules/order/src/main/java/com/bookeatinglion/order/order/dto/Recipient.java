package com.bookeatinglion.order.order.dto;

import jakarta.validation.constraints.NotBlank;

public record Recipient(
        @NotBlank String name, @NotBlank String phone, @NotBlank String postalCode, @NotBlank String address) {}

package com.bookeatinglion.order.order.dto;

import jakarta.validation.constraints.NotBlank;

public record RequestReturnRequest(@NotBlank String reason) {}

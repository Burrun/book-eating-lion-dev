package com.bookeatinglion.book.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReadingProgressRequest(
        @NotBlank String cfi,
        @Min(0) @Max(100) Integer percentage
) {
}

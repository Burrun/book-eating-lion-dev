package com.bookeatinglion.book.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FaqUpdateRequest(
        @NotBlank @Size(max = 50) String category,
        @NotBlank @Size(max = 500) String question,
        @NotBlank @Size(max = 4000) String answer,
        @Min(0) int sortOrder,
        boolean active) {}

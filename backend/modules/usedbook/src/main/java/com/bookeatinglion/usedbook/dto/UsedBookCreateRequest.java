package com.bookeatinglion.usedbook.dto;

import com.bookeatinglion.usedbook.domain.UsedBookCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record UsedBookCreateRequest(
        @NotBlank String isbn,
        @NotBlank String title,
        String author,
        String publisher,
        String coverImageUrl,
        @Positive int price,
        @NotNull UsedBookCondition condition,
        String description,
        @NotEmpty List<@NotBlank String> imageUrls
) {
}

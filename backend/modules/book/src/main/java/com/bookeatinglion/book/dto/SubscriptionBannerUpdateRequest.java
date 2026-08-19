package com.bookeatinglion.book.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record SubscriptionBannerUpdateRequest(
        @NotBlank @Size(max = 500) String imageUrl,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 500) String linkUrl,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        @Min(0) int sortOrder,
        boolean active) {}

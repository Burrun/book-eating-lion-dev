package com.bookeatinglion.book.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        @NotBlank @Size(max = 100) String categoryName, Long parentId, @Min(0) int sortOrder) {}

package com.bookeatinglion.s3.dto;

import jakarta.validation.constraints.NotBlank;

public record PresignedUrlRequest(
        @NotBlank String fileName
) {
}

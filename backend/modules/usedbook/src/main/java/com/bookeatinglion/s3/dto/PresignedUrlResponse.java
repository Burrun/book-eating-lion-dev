package com.bookeatinglion.s3.dto;

public record PresignedUrlResponse(
        String uploadUrl,
        String fileUrl,
        String key
) {
}

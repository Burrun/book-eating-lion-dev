package com.bookeatinglion.book.dto;

import java.time.OffsetDateTime;

public record EbookAccessResponse(Long bookId, boolean ebookAvailable, String presignedUrl, OffsetDateTime expiresAt) {

    public static EbookAccessResponse unavailable(Long bookId) {
        return new EbookAccessResponse(bookId, false, null, null);
    }
}

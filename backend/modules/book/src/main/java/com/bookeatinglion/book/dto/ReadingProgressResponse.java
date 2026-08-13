package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.ReadingProgress;

import java.time.LocalDateTime;

public record ReadingProgressResponse(
        Long bookId,
        String cfi,
        Integer percentage,
        LocalDateTime updatedAt
) {
    public static ReadingProgressResponse from(ReadingProgress readingProgress) {
        return new ReadingProgressResponse(
                readingProgress.getBook().getBookId(),
                readingProgress.getCfi(),
                readingProgress.getPercentage(),
                readingProgress.getUpdatedAt()
        );
    }
}

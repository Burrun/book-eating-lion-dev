package com.bookeatinglion.usedbook.dto;

import com.bookeatinglion.usedbook.domain.UsedBook;
import com.bookeatinglion.usedbook.domain.UsedBookCondition;
import com.bookeatinglion.usedbook.domain.UsedBookStatus;

import java.time.LocalDateTime;

public record UsedBookSummaryResponse(
        Long id,
        String isbn,
        String title,
        String coverImageUrl,
        int price,
        UsedBookCondition condition,
        UsedBookStatus status,
        LocalDateTime createdAt
) {
    public static UsedBookSummaryResponse from(UsedBook usedBook) {
        return new UsedBookSummaryResponse(
                usedBook.getId(),
                usedBook.getIsbn(),
                usedBook.getTitle(),
                usedBook.getCoverImageUrl(),
                usedBook.getPrice(),
                usedBook.getCondition(),
                usedBook.getStatus(),
                usedBook.getCreatedAt()
        );
    }
}

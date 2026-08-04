package com.bookeatinglion.usedbook.dto;

import com.bookeatinglion.usedbook.domain.UsedBook;
import com.bookeatinglion.usedbook.domain.UsedBookCondition;
import com.bookeatinglion.usedbook.domain.UsedBookStatus;

import java.time.LocalDateTime;
import java.util.List;

public record UsedBookResponse(
        Long id,
        String sellerId,
        String isbn,
        String title,
        String author,
        String publisher,
        String coverImageUrl,
        int price,
        UsedBookCondition condition,
        String description,
        UsedBookStatus status,
        List<String> imageUrls,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UsedBookResponse from(UsedBook usedBook) {
        return new UsedBookResponse(
                usedBook.getId(),
                usedBook.getSellerId(),
                usedBook.getIsbn(),
                usedBook.getTitle(),
                usedBook.getAuthor(),
                usedBook.getPublisher(),
                usedBook.getCoverImageUrl(),
                usedBook.getPrice(),
                usedBook.getCondition(),
                usedBook.getDescription(),
                usedBook.getStatus(),
                usedBook.getImageUrls(),
                usedBook.getCreatedAt(),
                usedBook.getUpdatedAt()
        );
    }
}

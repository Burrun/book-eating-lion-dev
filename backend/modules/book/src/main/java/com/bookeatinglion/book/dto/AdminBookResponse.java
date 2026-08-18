package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminBookResponse(
        Long bookId,
        String title,
        String author,
        String publisher,
        String isbn,
        String category,
        int price,
        String coverImageUrl,
        String description,
        String detailedSynopsis,
        boolean ebookAvailable,
        String epubS3Key,
        SaleStatus saleStatus,
        LocalDate publishedDate,
        int salesCount,
        BigDecimal averageRating,
        int reviewCount,
        boolean deleted,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public static AdminBookResponse from(Book book) {
        return new AdminBookResponse(
                book.getBookId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPublisher(),
                book.getIsbn(),
                book.getCategory(),
                book.getPrice(),
                book.getCoverImageUrl(),
                book.getDescription(),
                book.getDetailedSynopsis(),
                book.isEbookAvailable(),
                book.getEpubS3Key(),
                book.getSaleStatus(),
                book.getPublishedDate(),
                book.getSalesCount(),
                book.getAverageRating(),
                book.getReviewCount(),
                book.isDeleted(),
                book.getDeletedAt(),
                book.getCreatedAt(),
                book.getUpdatedAt());
    }
}

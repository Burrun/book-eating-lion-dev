package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookDetailResponse(
        Long id,
        String title,
        String author,
        String publisher,
        String isbn,
        String category,
        int price,
        int stockQuantity,
        String coverImageUrl,
        String description,
        SaleStatus saleStatus,
        LocalDate publishedDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BookDetailResponse from(Book book) {
        return new BookDetailResponse(
                book.getBookId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPublisher(),
                book.getIsbn(),
                book.getCategory(),
                book.getPrice(),
                book.getStockQuantity(),
                book.getCoverImageUrl(),
                book.getDescription(),
                book.getSaleStatus(),
                book.getPublishedDate(),
                book.getCreatedAt(),
                book.getUpdatedAt()
        );
    }
}

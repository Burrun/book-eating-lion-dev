package com.bookeatinglion.admin.dto;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminBookResponse(
        Long id,
        String title,
        String author,
        String publisher,
        String isbn,
        String category,
        int price,
        int stockQuantity,
        int salesCount,
        String coverImageUrl,
        SaleStatus saleStatus,
        LocalDate publishedDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminBookResponse from(Book book) {
        return new AdminBookResponse(
                book.getBookId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPublisher(),
                book.getIsbn(),
                book.getCategory(),
                book.getPrice(),
                book.getStockQuantity(),
                book.getSalesCount(),
                book.getCoverImageUrl(),
                book.getSaleStatus(),
                book.getPublishedDate(),
                book.getCreatedAt(),
                book.getUpdatedAt()
        );
    }
}

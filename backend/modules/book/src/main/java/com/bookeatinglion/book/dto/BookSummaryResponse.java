package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;

public record BookSummaryResponse(
        Long id,
        String title,
        String author,
        int price,
        String coverImageUrl,
        String category,
        SaleStatus saleStatus
) {
    public static BookSummaryResponse from(Book book) {
        return new BookSummaryResponse(
                book.getBookId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPrice(),
                book.getCoverImageUrl(),
                book.getCategory(),
                book.getSaleStatus()
        );
    }
}

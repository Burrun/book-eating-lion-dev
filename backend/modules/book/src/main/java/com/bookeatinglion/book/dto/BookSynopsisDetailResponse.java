package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Book;

public record BookSynopsisDetailResponse(
        Long bookId,
        String title,
        String detailedSynopsis
) {
    public static BookSynopsisDetailResponse from(Book book) {
        return new BookSynopsisDetailResponse(
                book.getBookId(),
                book.getTitle(),
                book.getDetailedSynopsis()
        );
    }
}

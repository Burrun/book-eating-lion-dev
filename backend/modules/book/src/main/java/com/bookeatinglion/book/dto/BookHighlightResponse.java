package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.BookHighlight;
import java.time.LocalDateTime;

public record BookHighlightResponse(
        Long highlightId,
        Long bookId,
        String bookTitle,
        String cfiRange,
        String selectedText,
        String memoText,
        LocalDateTime createdAt) {

    public static BookHighlightResponse from(BookHighlight highlight) {
        return new BookHighlightResponse(
                highlight.getBookHighlightId(),
                highlight.getBook().getBookId(),
                highlight.getBook().getTitle(),
                highlight.getCfiRange(),
                highlight.getSelectedText(),
                highlight.getMemoText(),
                highlight.getCreatedAt());
    }
}

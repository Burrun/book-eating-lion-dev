package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.BookMemo;
import java.time.LocalDateTime;

public record BookMemoResponse(Long bookId, String memoText, LocalDateTime fedAt, LocalDateTime updatedAt) {
    public static BookMemoResponse from(BookMemo memo) {
        return new BookMemoResponse(
                memo.getBook().getBookId(), memo.getMemoText(), memo.getFedAt(), memo.getUpdatedAt());
    }
}

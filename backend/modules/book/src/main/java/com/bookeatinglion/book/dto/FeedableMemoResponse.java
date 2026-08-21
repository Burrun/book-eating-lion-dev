package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.BookMemo;

/** LionFeedingCard가 드래그 카드로 그리는 "아직 안 먹인 메모" 하나. */
public record FeedableMemoResponse(Long bookId, String bookTitle, String memoText) {
    public static FeedableMemoResponse from(BookMemo memo) {
        return new FeedableMemoResponse(
                memo.getBook().getBookId(), memo.getBook().getTitle(), memo.getMemoText());
    }
}

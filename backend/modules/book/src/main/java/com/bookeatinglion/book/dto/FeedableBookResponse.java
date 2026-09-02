package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.ReadingProgress;

/** 마이페이지 사자 먹이기 카드 1장 — 완독했지만 아직 안 먹인 책. */
public record FeedableBookResponse(Long bookId, String bookTitle, String coverImageUrl, Integer percentage) {

    public static FeedableBookResponse from(ReadingProgress progress) {
        return new FeedableBookResponse(
                progress.getBook().getBookId(),
                progress.getBook().getTitle(),
                progress.getBook().getCoverImageUrl(),
                progress.getPercentage());
    }
}

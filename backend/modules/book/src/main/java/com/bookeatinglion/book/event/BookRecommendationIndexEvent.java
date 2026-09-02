package com.bookeatinglion.book.event;

import com.bookeatinglion.book.domain.Book;

/** 도서 트랜잭션 커밋 뒤 추천 벡터 인덱스에 반영할 스냅샷. */
public record BookRecommendationIndexEvent(
        Action action, long bookId, String title, String author, String category, String description) {

    public static BookRecommendationIndexEvent upsert(Book book) {
        return new BookRecommendationIndexEvent(
                Action.UPSERT,
                book.getBookId(),
                book.getTitle(),
                book.getAuthor(),
                book.getCategory(),
                book.getDescription());
    }

    public static BookRecommendationIndexEvent delete(long bookId) {
        return new BookRecommendationIndexEvent(Action.DELETE, bookId, "", "", "", "");
    }

    public enum Action {
        UPSERT,
        DELETE
    }
}

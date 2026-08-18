package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Book;

public record RecommendationCardResponse(
        Long bookId,
        String title,
        String author,
        String category,
        int price,
        String coverImageUrl,
        double score,
        String recommendationReason) {

    public static RecommendationCardResponse of(Book book, double score, String reason) {
        return new RecommendationCardResponse(
                book.getBookId(),
                book.getTitle(),
                book.getAuthor(),
                book.getCategory(),
                book.getPrice(),
                book.getCoverImageUrl(),
                score,
                reason);
    }
}

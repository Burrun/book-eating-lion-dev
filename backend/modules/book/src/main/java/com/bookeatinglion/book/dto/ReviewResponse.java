package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Review;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long bookId,
        Long memberId,
        int rating,
        String content,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getReviewId(),
                review.getBook().getBookId(),
                review.getMemberId(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt()
        );
    }
}

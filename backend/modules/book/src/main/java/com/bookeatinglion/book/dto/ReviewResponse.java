package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Review;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long bookId,
        Long memberId,
        // members 를 조인하지 않고 작성 시점 스냅샷을 그대로 내보낸다.
        String nickname,
        int rating,
        String content,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getReviewId(),
                review.getBook().getBookId(),
                review.getMemberId(),
                review.getNickname(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt()
        );
    }
}

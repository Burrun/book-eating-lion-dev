package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Review;
import java.time.LocalDateTime;

/** 마이페이지에서 현재 회원이 작성한 리뷰를 도서 정보와 함께 보여주기 위한 응답. */
public record MemberReviewResponse(
        Long id, Long bookId, String bookTitle, int rating, String content, LocalDateTime createdAt) {

    public static MemberReviewResponse from(Review review) {
        return new MemberReviewResponse(
                review.getReviewId(),
                review.getBook().getBookId(),
                review.getBook().getTitle(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt());
    }
}

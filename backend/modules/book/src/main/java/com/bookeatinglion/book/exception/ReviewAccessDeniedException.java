package com.bookeatinglion.book.exception;

public class ReviewAccessDeniedException extends RuntimeException {

    public ReviewAccessDeniedException(Long reviewId, Long memberId) {
        super("Member " + memberId + " is not allowed to delete review " + reviewId);
    }
}

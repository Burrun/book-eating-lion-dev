package com.bookeatinglion.book.exception;

public class ReviewAccessDeniedException extends RuntimeException {

    public ReviewAccessDeniedException(Long reviewId, String memberId) {
        super("Member " + memberId + " is not allowed to modify review " + reviewId);
    }
}

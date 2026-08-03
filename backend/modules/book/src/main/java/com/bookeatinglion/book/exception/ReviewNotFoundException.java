package com.bookeatinglion.book.exception;

public class ReviewNotFoundException extends RuntimeException {

    public ReviewNotFoundException(Long reviewId) {
        super("Review not found: id=" + reviewId);
    }
}

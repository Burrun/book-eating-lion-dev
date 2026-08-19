package com.bookeatinglion.book.exception;

public class InvalidRecommendationReactionException extends RuntimeException {

    public InvalidRecommendationReactionException() {
        super("현재 추천 대기열에 노출된 도서가 아닙니다.");
    }
}

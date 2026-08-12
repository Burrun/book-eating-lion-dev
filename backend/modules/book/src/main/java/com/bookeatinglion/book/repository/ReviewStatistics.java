package com.bookeatinglion.book.repository;

/** 도서 리뷰 평균과 개수를 한 번의 집계 쿼리로 조회한다. */
public interface ReviewStatistics {

    Double getAverageRating();

    Long getReviewCount();
}

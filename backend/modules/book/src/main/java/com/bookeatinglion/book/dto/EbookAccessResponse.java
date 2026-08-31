package com.bookeatinglion.book.dto;

import java.time.OffsetDateTime;

/**
 * 🔴 {@code purchased} 는 "열람 가능"과 다르다. 열람은 구매 OR 구독이면 되지만(EbookService),
 * 사자 RAG의 검색 권한 근거는 ai_db.purchased_books — 즉 <b>구매 이벤트뿐</b>이다. 구독으로
 * 읽는 중인 책에 사자를 붙이면 "구매한 책에서 근거를 찾지 못했습니다"만 나오므로, 뷰어가
 * 이 값으로 사자 진입점 노출 여부를 정한다.
 */
public record EbookAccessResponse(
        Long bookId, boolean ebookAvailable, boolean purchased, String presignedUrl, OffsetDateTime expiresAt) {

    public static EbookAccessResponse unavailable(Long bookId) {
        return new EbookAccessResponse(bookId, false, false, null, null);
    }
}

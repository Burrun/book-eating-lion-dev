package com.bookeatinglion.book.dto;

import java.time.OffsetDateTime;

/**
 * {@code purchased} 는 "열람 가능"과 다르다. 열람은 구매 OR 구독이면 된다(EbookService).
 *
 * <p>예전에는 뷰어가 이 값으로 사자 진입점 노출을 정했다 — RAG 검색 권한이 구매 이벤트만
 * 근거로 삼던 시절엔 구독 열람 중에 물으면 "근거를 찾지 못했습니다"만 나왔기 때문이다.
 * 지금은 {@code WikiRagService#allowedBooks} 가 구독 회원에게도 인제스트된 책 전체를 열어주므로
 * 그 용도는 사라졌다. 화면에서 "구매함"과 "구독으로 열람 중"을 구분해야 할 때 쓴다.
 */
public record EbookAccessResponse(
        Long bookId, boolean ebookAvailable, boolean purchased, String presignedUrl, OffsetDateTime expiresAt) {

    public static EbookAccessResponse unavailable(Long bookId) {
        return new EbookAccessResponse(bookId, false, false, null, null);
    }
}

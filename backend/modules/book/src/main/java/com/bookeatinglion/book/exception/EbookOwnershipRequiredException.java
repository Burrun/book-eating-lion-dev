package com.bookeatinglion.book.exception;

/** 구매 확정(review_permissions) 기록이 없는 회원이 eBook 열람 URL을 요청할 때 던진다. */
public class EbookOwnershipRequiredException extends RuntimeException {

    public EbookOwnershipRequiredException(Long bookId) {
        super("구매 확정 내역이 없어 eBook을 열람할 수 없습니다: bookId=" + bookId);
    }
}

package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.BookMemo;

/** "사자에게 물어보기" 패널이 그리는 "내가 먹인 요약 메모" 목록 항목 하나. */
public record FedMemoResponse(Long bookId, String bookTitle, String memoText) {
    public static FedMemoResponse from(BookMemo memo) {
        return new FedMemoResponse(memo.getBook().getBookId(), memo.getBook().getTitle(), memo.getMemoText());
    }
}

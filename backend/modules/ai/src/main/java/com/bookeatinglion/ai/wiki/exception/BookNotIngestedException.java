package com.bookeatinglion.ai.wiki.exception;

public class BookNotIngestedException extends RuntimeException {

    public BookNotIngestedException(Long bookId) {
        super("아직 인제스트되지 않은 책입니다: " + bookId);
    }
}

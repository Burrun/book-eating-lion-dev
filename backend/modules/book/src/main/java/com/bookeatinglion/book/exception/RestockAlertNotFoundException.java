package com.bookeatinglion.book.exception;

public class RestockAlertNotFoundException extends RuntimeException {
    public RestockAlertNotFoundException(Long bookId, String memberId) {
        super("Restock alert not found: bookId=" + bookId + ", memberId=" + memberId);
    }
}

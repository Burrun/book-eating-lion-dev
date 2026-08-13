package com.bookeatinglion.book.exception;

public class RestockAlertConflictException extends RuntimeException {
    public RestockAlertConflictException(Long bookId, String memberId) {
        super("Restock alert already waiting: bookId=" + bookId + ", memberId=" + memberId);
    }
}

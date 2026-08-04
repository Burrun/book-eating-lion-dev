package com.bookeatinglion.book.exception;

public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(Long bookId) {
        super("Book not found: id=" + bookId);
    }
}

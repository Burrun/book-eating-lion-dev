package com.bookeatinglion.book.exception;

public class HighlightNotFoundException extends RuntimeException {

    public HighlightNotFoundException(Long highlightId) {
        super("Highlight not found: id=" + highlightId);
    }
}

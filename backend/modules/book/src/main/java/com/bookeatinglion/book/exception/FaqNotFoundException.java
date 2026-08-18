package com.bookeatinglion.book.exception;

public class FaqNotFoundException extends RuntimeException {
    public FaqNotFoundException(Long faqId) {
        super("FAQ not found: id=" + faqId);
    }
}

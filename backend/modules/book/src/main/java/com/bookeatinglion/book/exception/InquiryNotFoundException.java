package com.bookeatinglion.book.exception;

public class InquiryNotFoundException extends RuntimeException {
    public InquiryNotFoundException(Long inquiryId) {
        super("Inquiry not found: id=" + inquiryId);
    }
}

package com.bookeatinglion.book.exception;

public class InquiryAccessDeniedException extends RuntimeException {
    public InquiryAccessDeniedException(Long inquiryId, String memberId) {
        super("Inquiry access denied: inquiryId=" + inquiryId + ", memberId=" + memberId);
    }
}

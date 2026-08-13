package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.InquiryStatus;
import com.bookeatinglion.book.domain.ProductInquiry;
import java.time.LocalDateTime;

public record InquiryResponse(
        Long inquiryId,
        Long bookId,
        String memberId,
        String title,
        String content,
        boolean privateInquiry,
        InquiryStatus status,
        String answer,
        String answeredBy,
        LocalDateTime answeredAt,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static InquiryResponse from(ProductInquiry inquiry) {
        return new InquiryResponse(
                inquiry.getInquiryId(),
                inquiry.getBook().getBookId(),
                inquiry.getMemberId(),
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.isPrivateInquiry(),
                inquiry.getStatus(),
                inquiry.getAnswer(),
                inquiry.getAnsweredBy(),
                inquiry.getAnsweredAt(),
                inquiry.isDeleted(),
                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt());
    }
}

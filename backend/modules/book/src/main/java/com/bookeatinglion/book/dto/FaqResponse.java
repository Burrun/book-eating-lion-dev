package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Faq;
import java.time.LocalDateTime;

public record FaqResponse(
        Long faqId,
        String category,
        String question,
        String answer,
        int sortOrder,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static FaqResponse from(Faq faq) {
        return new FaqResponse(
                faq.getFaqId(),
                faq.getCategory(),
                faq.getQuestion(),
                faq.getAnswer(),
                faq.getSortOrder(),
                faq.isActive(),
                faq.getCreatedAt(),
                faq.getUpdatedAt());
    }
}

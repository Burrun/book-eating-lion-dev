package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.RestockAlert;
import com.bookeatinglion.book.domain.RestockAlertStatus;
import java.time.LocalDateTime;

public record RestockAlertResponse(
        Long restockAlertId,
        Long bookId,
        String title,
        RestockAlertStatus status,
        int retryCount,
        LocalDateTime requestedAt,
        LocalDateTime notifiedAt,
        LocalDateTime cancelledAt) {
    public static RestockAlertResponse from(RestockAlert alert) {
        return new RestockAlertResponse(
                alert.getRestockAlertId(),
                alert.getBook().getBookId(),
                alert.getBook().getTitle(),
                alert.getStatus(),
                alert.getRetryCount(),
                alert.getRequestedAt(),
                alert.getNotifiedAt(),
                alert.getCancelledAt());
    }
}

package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.RestockAlert;
import com.bookeatinglion.book.domain.RestockAlertStatus;
import com.bookeatinglion.book.repository.RestockAlertRepository;
import java.time.LocalDateTime;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestockAlertStateService {
    private final RestockAlertRepository alertRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliveryTarget claimWaiting(Long alertId) {
        return claim(alertId, EnumSet.of(RestockAlertStatus.WAITING));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliveryTarget claimFailed(Long alertId) {
        return claim(alertId, EnumSet.of(RestockAlertStatus.FAILED));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long alertId) {
        alertRepository.findByIdForUpdate(alertId).ifPresent(alert -> {
            if (alert.getStatus() == RestockAlertStatus.PROCESSING) {
                alert.sent(LocalDateTime.now());
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long alertId, String error, LocalDateTime nextRetryAt) {
        alertRepository.findByIdForUpdate(alertId).ifPresent(alert -> {
            if (alert.getStatus() == RestockAlertStatus.PROCESSING) {
                alert.failed(error, nextRetryAt);
            }
        });
    }

    private DeliveryTarget claim(Long alertId, EnumSet<RestockAlertStatus> claimableStatuses) {
        RestockAlert alert = alertRepository.findByIdForUpdate(alertId).orElse(null);
        if (alert == null || !claimableStatuses.contains(alert.getStatus())) {
            return null;
        }

        alert.processing(LocalDateTime.now());
        Book book = alert.getBook();
        return new DeliveryTarget(
                alert.getRestockAlertId(),
                alert.getMemberId(),
                book.getBookId(),
                book.getTitle(),
                book.getAuthor(),
                book.getCoverImageUrl());
    }

    public record DeliveryTarget(
            Long alertId, String memberId, Long bookId, String bookTitle, String author, String coverImageUrl) {}
}

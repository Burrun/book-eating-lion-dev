package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.ProcessedRestockEvent;
import com.bookeatinglion.book.domain.RestockAlert;
import com.bookeatinglion.book.domain.RestockAlertStatus;
import com.bookeatinglion.book.dto.RestockAlertResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.RestockAlertConflictException;
import com.bookeatinglion.book.exception.RestockAlertNotFoundException;
import com.bookeatinglion.book.port.MemberNotificationProfilePort;
import com.bookeatinglion.book.port.RestockEmailSender;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ProcessedRestockEventRepository;
import com.bookeatinglion.book.repository.RestockAlertRepository;
import com.bookeatinglion.common.event.InventoryRestockedEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestockAlertService {
    private final RestockAlertRepository alertRepository;
    private final ProcessedRestockEventRepository processedEventRepository;
    private final BookRepository bookRepository;
    private final MemberNotificationProfilePort memberProfilePort;
    private final RestockEmailSender emailSender;
    private RestockAlertStateService alertStateService;

    @Autowired
    void setAlertStateService(RestockAlertStateService alertStateService) {
        this.alertStateService = alertStateService;
    }

    @Value("${notifications.restock.max-retries:3}")
    private int maxRetries;

    @Value("${notifications.restock.retry-delay:PT5M}")
    private Duration retryDelay;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    public RestockAlertResponse subscribe(Long bookId, String memberId) {
        Book book = bookRepository
                .findByBookIdAndIsDeletedFalse(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        LocalDateTime now = LocalDateTime.now();
        RestockAlert alert = alertRepository
                .findByMemberIdAndBookBookId(memberId, bookId)
                .map(existing -> {
                    if (existing.getStatus() == RestockAlertStatus.WAITING
                            || existing.getStatus() == RestockAlertStatus.PROCESSING) {
                        throw new RestockAlertConflictException(bookId, memberId);
                    }
                    existing.reactivate(now);
                    return existing;
                })
                .orElseGet(() -> RestockAlert.builder()
                        .book(book)
                        .memberId(memberId)
                        .requestedAt(now)
                        .build());
        return RestockAlertResponse.from(alertRepository.save(alert));
    }

    public List<RestockAlertResponse> getMyAlerts(String memberId, RestockAlertStatus status) {
        List<RestockAlert> alerts = status == null
                ? alertRepository.findByMemberIdOrderByRequestedAtDesc(memberId)
                : alertRepository.findByMemberIdAndStatusOrderByRequestedAtDesc(memberId, status);
        return alerts.stream().map(RestockAlertResponse::from).toList();
    }

    @Transactional
    public void cancel(Long bookId, String memberId) {
        RestockAlert alert = alertRepository
                .findByMemberIdAndBookBookId(memberId, bookId)
                .orElseThrow(() -> new RestockAlertNotFoundException(bookId, memberId));
        alert.cancel(LocalDateTime.now());
    }

    public void handleRestocked(InventoryRestockedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) return;
        if (event.previousStock() == 0 && event.currentStock() > 0) {
            alertRepository
                    .findByBookBookIdAndStatus(event.bookId(), RestockAlertStatus.WAITING)
                    .forEach(alert -> deliver(alert, RestockAlertStatus.WAITING));
        }
        processedEventRepository.save(new ProcessedRestockEvent(event.eventId(), event.bookId(), LocalDateTime.now()));
    }

    public int retryFailed() {
        List<RestockAlert> alerts =
                alertRepository.findTop100ByStatusAndNextRetryAtLessThanEqualAndRetryCountLessThanOrderByNextRetryAtAsc(
                        RestockAlertStatus.FAILED, LocalDateTime.now(), maxRetries);
        alerts.forEach(alert -> deliver(alert, RestockAlertStatus.FAILED));
        return alerts.size();
    }

    private void deliver(RestockAlert alert, RestockAlertStatus expectedStatus) {
        RestockAlertStateService.DeliveryTarget target = claim(alert, expectedStatus);
        if (target == null) return;

        try {
            var profile = memberProfilePort.findByMemberId(target.memberId());
            emailSender.send(new RestockEmailSender.RestockEmail(
                    profile.email(),
                    profile.name(),
                    target.bookTitle(),
                    target.author(),
                    target.coverImageUrl(),
                    frontendUrl + "/books/" + target.bookId()));
            markSent(alert, target.alertId());
        } catch (Exception e) {
            markFailed(alert, target.alertId(), e);
            log.warn("재입고 이메일 발송 실패: alertId={}", target.alertId(), e);
        }
    }

    private RestockAlertStateService.DeliveryTarget claim(RestockAlert alert, RestockAlertStatus expectedStatus) {
        if (alertStateService != null) {
            return expectedStatus == RestockAlertStatus.WAITING
                    ? alertStateService.claimWaiting(alert.getRestockAlertId())
                    : alertStateService.claimFailed(alert.getRestockAlertId());
        }

        // 단위 테스트처럼 Spring 트랜잭션 프록시가 없는 경우에도 기존 동작을 유지한다.
        if (alert.getStatus() != expectedStatus) return null;
        alert.processing(LocalDateTime.now());
        Book book = alert.getBook();
        return new RestockAlertStateService.DeliveryTarget(
                alert.getRestockAlertId(),
                alert.getMemberId(),
                book.getBookId(),
                book.getTitle(),
                book.getAuthor(),
                book.getCoverImageUrl());
    }

    private void markSent(RestockAlert alert, Long alertId) {
        if (alertStateService == null) {
            alert.sent(LocalDateTime.now());
            return;
        }
        alertStateService.markSent(alertId);
    }

    private void markFailed(RestockAlert alert, Long alertId, Exception exception) {
        LocalDateTime nextRetryAt = LocalDateTime.now().plus(retryDelay);
        if (alertStateService == null) {
            alert.failed(exception.getMessage(), nextRetryAt);
            return;
        }
        alertStateService.markFailed(alertId, exception.getMessage(), nextRetryAt);
    }
}

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

    @Transactional
    public void handleRestocked(InventoryRestockedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) return;
        if (event.previousStock() == 0 && event.currentStock() > 0) {
            alertRepository
                    .findByBookBookIdAndStatus(event.bookId(), RestockAlertStatus.WAITING)
                    .forEach(this::deliver);
        }
        processedEventRepository.save(new ProcessedRestockEvent(event.eventId(), event.bookId(), LocalDateTime.now()));
    }

    @Transactional
    public int retryFailed() {
        List<RestockAlert> alerts =
                alertRepository.findTop100ByStatusAndNextRetryAtLessThanEqualAndRetryCountLessThanOrderByNextRetryAtAsc(
                        RestockAlertStatus.FAILED, LocalDateTime.now(), maxRetries);
        alerts.forEach(this::deliver);
        return alerts.size();
    }

    private void deliver(RestockAlert alert) {
        alert.processing(LocalDateTime.now());
        try {
            var profile = memberProfilePort.findByMemberId(alert.getMemberId());
            Book book = alert.getBook();
            emailSender.send(new RestockEmailSender.RestockEmail(
                    profile.email(),
                    profile.name(),
                    book.getTitle(),
                    book.getAuthor(),
                    book.getCoverImageUrl(),
                    frontendUrl + "/books/" + book.getBookId()));
            alert.sent(LocalDateTime.now());
        } catch (Exception e) {
            alert.failed(e.getMessage(), LocalDateTime.now().plus(retryDelay));
            log.warn("재입고 이메일 발송 실패: alertId={}, retryCount={}", alert.getRestockAlertId(), alert.getRetryCount(), e);
        }
    }
}

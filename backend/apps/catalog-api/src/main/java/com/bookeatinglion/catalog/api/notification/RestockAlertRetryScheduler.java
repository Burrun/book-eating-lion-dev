package com.bookeatinglion.catalog.api.notification;

import com.bookeatinglion.book.service.RestockAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RestockAlertRetryScheduler {
    private final RestockAlertService restockAlertService;

    @Scheduled(fixedDelayString = "${notifications.restock.retry-scan-delay:60000}")
    public void retryFailedEmails() {
        restockAlertService.retryFailed();
    }
}

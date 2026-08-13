package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "restock_alerts", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "book_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestockAlert extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "restock_alert_id")
    private Long restockAlertId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "member_id", nullable = false, length = 255)
    private String memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestockAlertStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(length = 1000)
    private String lastError;

    private LocalDateTime requestedAt;
    private LocalDateTime lastAttemptedAt;
    private LocalDateTime nextRetryAt;
    private LocalDateTime notifiedAt;
    private LocalDateTime cancelledAt;

    @Builder
    public RestockAlert(Book book, String memberId, LocalDateTime requestedAt) {
        this.book = book;
        this.memberId = memberId;
        this.status = RestockAlertStatus.WAITING;
        this.requestedAt = requestedAt;
    }

    public void reactivate(LocalDateTime now) {
        status = RestockAlertStatus.WAITING;
        retryCount = 0;
        lastError = null;
        requestedAt = now;
        lastAttemptedAt = null;
        nextRetryAt = null;
        notifiedAt = null;
        cancelledAt = null;
    }

    public void processing(LocalDateTime now) {
        status = RestockAlertStatus.PROCESSING;
        lastAttemptedAt = now;
        nextRetryAt = null;
    }

    public void sent(LocalDateTime now) {
        status = RestockAlertStatus.SENT;
        notifiedAt = now;
        lastError = null;
        nextRetryAt = null;
    }

    public void failed(String error, LocalDateTime retryAt) {
        status = RestockAlertStatus.FAILED;
        retryCount++;
        lastError = error == null ? "Unknown email delivery error" : error.substring(0, Math.min(error.length(), 1000));
        nextRetryAt = retryAt;
    }

    public void cancel(LocalDateTime now) {
        status = RestockAlertStatus.CANCELLED;
        cancelledAt = now;
        nextRetryAt = null;
    }
}

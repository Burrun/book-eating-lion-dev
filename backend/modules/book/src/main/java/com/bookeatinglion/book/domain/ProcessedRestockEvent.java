package com.bookeatinglion.book.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "processed_restock_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedRestockEvent {
    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public ProcessedRestockEvent(String eventId, Long bookId, LocalDateTime processedAt) {
        this.eventId = eventId;
        this.bookId = bookId;
        this.processedAt = processedAt;
    }
}

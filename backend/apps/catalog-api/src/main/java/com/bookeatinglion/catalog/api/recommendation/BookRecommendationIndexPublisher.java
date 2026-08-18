package com.bookeatinglion.catalog.api.recommendation;

import com.bookeatinglion.book.event.BookRecommendationIndexEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Catalog DB 커밋이 끝난 도서 변경만 AI 추천 인덱서에 전달한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookRecommendationIndexPublisher {

    public static final String STREAM_KEY = "events:catalog:recommendation-books";

    private final StringRedisTemplate redis;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(BookRecommendationIndexEvent event) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("eventId", UUID.randomUUID().toString());
        values.put("occurredAt", Instant.now().toString());
        values.put("action", event.action().name());
        values.put("bookId", Long.toString(event.bookId()));
        values.put("title", safe(event.title()));
        values.put("author", safe(event.author()));
        values.put("category", safe(event.category()));
        values.put("description", safe(event.description()));
        try {
            redis.opsForStream().add(StreamRecords.mapBacked(values).withStreamKey(STREAM_KEY));
        } catch (RuntimeException e) {
            // 관리자 CRUD 자체는 성공시킨다. 누락분은 rebuild API로 복구할 수 있다.
            log.error("추천 도서 인덱스 이벤트 발행 실패. action={}, bookId={}", event.action(), event.bookId(), e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

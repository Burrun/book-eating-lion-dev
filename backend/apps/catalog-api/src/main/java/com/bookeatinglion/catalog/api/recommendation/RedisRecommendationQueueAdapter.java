package com.bookeatinglion.catalog.api.recommendation;

import com.bookeatinglion.book.dto.RecommendationQueueResponse;
import com.bookeatinglion.book.port.RecommendationQueuePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisRecommendationQueueAdapter implements RecommendationQueuePort {

    private static final String KEY_PREFIX = "catalog:recommendation:queue:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<RecommendationQueueResponse> get(String memberId) {
        String json;
        try {
            json = redis.opsForValue().get(key(memberId));
        } catch (RuntimeException e) {
            log.warn("Redis 추천 대기열 조회 실패 — 새 대기열을 계산합니다. memberId={}", memberId, e);
            return Optional.empty();
        }
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, RecommendationQueueResponse.class));
        } catch (Exception e) {
            try {
                redis.delete(key(memberId));
            } catch (RuntimeException deleteFailure) {
                log.debug("손상된 추천 캐시 삭제 실패. memberId={}", memberId, deleteFailure);
            }
            return Optional.empty();
        }
    }

    @Override
    public void put(String memberId, RecommendationQueueResponse queue, Duration ttl) {
        try {
            redis.opsForValue().set(key(memberId), objectMapper.writeValueAsString(queue), ttl);
        } catch (Exception e) {
            log.warn("Redis 추천 대기열 저장 실패 — DB 노출 기록만 유지합니다. memberId={}", memberId, e);
        }
    }

    @Override
    public void removeCard(String memberId, UUID queueId, Long bookId, Duration ttl) {
        get(memberId).filter(queue -> queue.queueId().equals(queueId)).ifPresent(queue -> {
            var remaining = queue.cards().stream()
                    .filter(card -> !card.bookId().equals(bookId))
                    .toList();
            if (remaining.isEmpty()) {
                redis.delete(key(memberId));
            } else {
                put(memberId, new RecommendationQueueResponse(queue.queueId(), remaining), ttl);
            }
        });
    }

    private static String key(String memberId) {
        return KEY_PREFIX + memberId;
    }
}

package com.bookeatinglion.ai.wiki.service;

import com.bookeatinglion.ai.wiki.config.RagProperties;
import com.bookeatinglion.ai.wiki.exception.QuotaExceededException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 사용자별 일일 질의 상한.
 *
 * <p>🔴 <b>Redis 장애 시 통과시킨다(fail-open).</b> 쿼터는 과금 방어선이지 인증이 아니고,
 * Redis 는 여러 서비스가 공유하므로 여기서 막으면 장애 반경만 넓어진다. 대신 WARN 을 남긴다 —
 * 쿼터가 꺼진 걸 아무도 모르는 상태가 진짜 사고다.
 *
 * <p>접근 제어({@link FedBookCache})는 반대로 fail-open 하지 않는다. 둘을 같은 규칙으로
 * 다루면 안 된다.
 */
@Component
@RequiredArgsConstructor
public class DailyQuota {

    private static final Logger log = LoggerFactory.getLogger(DailyQuota.class);

    private final StringRedisTemplate redis;
    private final RagProperties props;

    public void consume(String memberId) {
        long used;
        try {
            String key = "ai:quota:%s:%s".formatted(memberId, LocalDate.now());
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return;
            }
            // 만료를 매번 다시 건다. 키에 날짜가 들어 있어 갱신해도 자정까지만 살고,
            // 첫 요청에만 걸면 그때 expire 가 실패한 키는 TTL 없이 영원히 남는다.
            redis.expire(key, Duration.ofSeconds(Math.max(1, secondsUntilMidnight())));
            used = count;
        } catch (DataAccessException e) {
            log.warn("Redis 장애로 일일 쿼터를 확인하지 못했다 — 통과시킨다(fail-open). memberId={}", memberId, e);
            return;
        }

        if (used > props.dailyQuota()) {
            throw new QuotaExceededException(secondsUntilMidnight());
        }
    }

    private static long secondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay())
                .toSeconds();
    }
}

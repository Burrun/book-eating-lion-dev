package com.bookeatinglion.ai.wiki.service;

import com.bookeatinglion.ai.wiki.repository.FedBookRepository;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 먹인 책 목록의 읽기 경로. 질의마다 fed_books 를 SELECT 하면 커넥션이 계속 열려 있어
 * {@code minimum-idle: 0} 이 무의미해진다 — 그래서 읽기는 Redis, 원본은 Postgres 다.
 *
 * <p>Redis 가 죽어도 DB 로 떨어져 계속 답한다. 이 목록은 접근 제어 필터라서
 * 쿼터(fail-open)와 달리 **틀리면 안 된다** — 캐시가 없으면 원본을 읽는 게 유일한 정답이다.
 */
@Component
@RequiredArgsConstructor
public class FedBookCache {

    private static final Logger log = LoggerFactory.getLogger(FedBookCache.class);

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final FedBookRepository fedBookRepository;

    /**
     * 빈 집합과 캐시 미스는 Redis 에서 구분되지 않는다 — Set 은 원소가 0이면 키 자체가 없다.
     * 그래서 한 권도 안 먹은 사용자는 매번 DB 를 1회 탄다. 그 사용자는 어차피 검색이
     * 성립하지 않으므로(교집합이 비어 검색을 건너뛴다) 문제가 되지 않는다.
     */
    public Set<Long> fedBookIds(String memberId) {
        String key = key(memberId);
        try {
            Set<String> cached = redis.opsForSet().members(key);
            if (cached != null && !cached.isEmpty()) {
                return parse(cached);
            }
        } catch (DataAccessException e) {
            log.warn("Redis 조회 실패 — fed_books 원본으로 떨어진다. memberId={}", memberId, e);
        }

        List<Long> fromDb = fedBookRepository.findBookIdsByMemberId(memberId);
        if (!fromDb.isEmpty()) {
            warm(key, fromDb);
        }
        return new LinkedHashSet<>(fromDb);
    }

    /**
     * 먹이기 성공 후 호출한다. 실패해도 예외를 올리지 않는다 — 원본은 이미 DB 에 들어갔다.
     *
     * <p>SADD 가 실패하면 이 회원의 캐시를 통째로 지운다. 부분 반영된 채로 두면
     * {@link #fedBookIds}가 "비어있지 않음"으로 보고 DB 대조 없이 그 stale 집합을 영원히
     * 돌려준다 — 이 책 하나만 빠진 채로. 지워두면 다음 조회가 DB 로 떨어져 전체를 다시
     * 채운다(방금 커밋된 이 먹이기 포함).
     */
    public void add(String memberId, Long bookId) {
        String key = key(memberId);
        try {
            redis.opsForSet().add(key, String.valueOf(bookId));
            redis.expire(key, TTL);
        } catch (DataAccessException e) {
            log.warn("Redis 갱신 실패 — 캐시를 무효화한다. memberId={} bookId={}", memberId, bookId, e);
            try {
                redis.delete(key);
            } catch (DataAccessException e2) {
                log.warn("Redis 캐시 무효화도 실패 — 다음 조회가 stale 데이터를 반환할 수 있다. memberId={}", memberId, e2);
            }
        }
    }

    private void warm(String key, List<Long> bookIds) {
        try {
            redis.opsForSet().add(key, bookIds.stream().map(String::valueOf).toArray(String[]::new));
            redis.expire(key, TTL);
        } catch (DataAccessException e) {
            log.warn("Redis 채우기 실패 — 이번 요청은 DB 값으로 계속 진행한다. key={}", key, e);
        }
    }

    private static Set<Long> parse(Set<String> raw) {
        Set<Long> ids = new LinkedHashSet<>(raw.size());
        for (String value : raw) {
            ids.add(Long.valueOf(value));
        }
        return ids;
    }

    private static String key(String memberId) {
        return "ai:fed:" + memberId;
    }
}

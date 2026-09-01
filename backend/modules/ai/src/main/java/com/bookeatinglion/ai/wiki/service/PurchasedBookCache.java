package com.bookeatinglion.ai.wiki.service;

import com.bookeatinglion.ai.wiki.repository.PurchasedBookRepository;
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
 * 구매한 책 목록의 읽기 경로.
 *
 * <p>검색 허용 목록의 <b>일부</b>다 — 구독 회원은 여기에 인제스트된 책 전체가 더해진다
 * ({@code WikiRagService#allowedBooks}). 이 클래스는 "구매"만 안다.
 */
@Component
@RequiredArgsConstructor
public class PurchasedBookCache {

    private static final Logger log = LoggerFactory.getLogger(PurchasedBookCache.class);

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final PurchasedBookRepository purchasedBookRepository;

    public Set<Long> purchasedBookIds(String memberId) {
        String key = key(memberId);
        try {
            Set<String> cached = redis.opsForSet().members(key);
            if (cached != null && !cached.isEmpty()) {
                return parse(cached);
            }
        } catch (DataAccessException e) {
            log.warn("Redis 조회 실패 — purchased_books 원본으로 떨어진다. memberId={}", memberId, e);
        }

        List<Long> fromDb = purchasedBookRepository.findBookIdsByMemberId(memberId);
        if (!fromDb.isEmpty()) {
            warm(key, fromDb);
        }
        return new LinkedHashSet<>(fromDb);
    }

    /**
     * SADD 가 실패하면 이 회원의 캐시를 통째로 지운다. 부분 반영된 채로 두면
     * {@link #purchasedBookIds}가 "비어있지 않음"으로 보고 DB 대조 없이 그 stale 집합을
     * 영원히 돌려준다 — 이 책 하나만 빠진 채로. 지워두면 다음 조회가 DB 로 떨어져
     * 전체를 다시 채운다(방금 커밋된 이 구매 포함).
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
        return "ai:purchased:" + memberId;
    }
}

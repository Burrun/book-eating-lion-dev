package com.bookeatinglion.ai.wiki.service;

import com.bookeatinglion.ai.client.MemberSubscriptionClient;
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
 * 사용자별 일일 질의 상한. 구독자와 비구독자의 상한이 다르다.
 *
 * <p>🔴 <b>Redis 장애 시 통과시킨다(fail-open).</b> 쿼터는 과금 방어선이지 인증이 아니고,
 * Redis 는 여러 서비스가 공유하므로 여기서 막으면 장애 반경만 넓어진다. 대신 WARN 을 남긴다 —
 * 쿼터가 꺼진 걸 아무도 모르는 상태가 진짜 사고다.
 *
 * <p>접근 제어({@link FedBookCache})는 반대로 fail-open 하지 않는다. 둘을 같은 규칙으로
 * 다루면 안 된다.
 *
 * <p>구독 조회는 무료 상한을 넘긴 뒤에만 한다. 대부분의 요청은 그 아래에서 끝나므로
 * member-service 를 부를 일이 없다 — 매 질의마다 부르면 RAG 경로에 불필요한 왕복이 붙는다.
 * 조회가 실패하면 {@code MemberSubscriptionClientFallback} 이 비구독으로 강등해 돌려주므로
 * 무료 상한이 적용된다(먹이기 EXP 배율과 같은 처리다).
 */
@Component
@RequiredArgsConstructor
public class DailyQuota {

    private static final Logger log = LoggerFactory.getLogger(DailyQuota.class);

    private final StringRedisTemplate redis;
    private final RagProperties props;
    private final MemberSubscriptionClient memberSubscriptionClient;

    /**
     * 상한에 도달했으면 던진다. <b>사용량을 늘리지 않는다.</b>
     *
     * <p>질의 처리 <i>전</i>에 부른다 — 이미 초과한 사용자에게 임베딩·벡터 검색 비용을
     * 태우지 않기 위해서다.
     */
    public void check(String memberId) {
        long used;
        try {
            String raw = redis.opsForValue().get(key(memberId));
            used = raw == null ? 0 : Long.parseLong(raw);
        } catch (DataAccessException e) {
            log.warn("Redis 장애로 일일 쿼터를 확인하지 못했다 — 통과시킨다(fail-open). memberId={}", memberId, e);
            return;
        } catch (NumberFormatException e) {
            log.warn("쿼터 키 값이 숫자가 아니다 — 통과시킨다. memberId={}", memberId, e);
            return;
        }

        // 무료 상한 안이면 구독 여부를 볼 필요가 없다.
        if (used < props.freeDailyQuota()) {
            return;
        }

        boolean subscribed =
                memberSubscriptionClient.getSubscriptionStatus(memberId).subscribed();
        long limit = subscribed ? props.subscribedDailyQuota() : props.freeDailyQuota();
        if (used >= limit) {
            throw new QuotaExceededException(secondsUntilMidnight());
        }
    }

    /**
     * 사용량을 1 늘린다. <b>요청을 실제로 처리한 뒤에만</b> 부른다.
     *
     * <p>🔴 예전에는 컨트롤러 진입 즉시 소모했는데, 그러면 아무 일도 하지 않았거나 서버
     * 잘못으로 실패한 질의까지 사용자 한도를 깎는다. 실제로 겪음(2026-08-31): 벡터 인덱스가
     * 비어 있어 근거를 못 찾던 응답과 Bedrock 권한 오류 500 이 무료 한도를 전부 소모했다.
     *
     * <p>기준은 "LLM 을 불렀는가"가 아니라 <b>"과금되는 일을 했는가"</b>다 — 질의 임베딩
     * (Bedrock Titan)과 벡터 검색도 비용이 든다. 그래서 LLM 을 부르지 않는 SEARCH 모드도
     * 센다. LLM 호출만 기준으로 삼으면 SEARCH 가 무제한 무료가 되어 임베딩 비용이 열린다.
     *
     * <p>다만 거리 가드에 걸려 grounded=false 로 끝난 질의는 임베딩·검색을 이미 했는데도
     * 세지 않는다 — "답을 못 줬으면 안 받는다"를 택한 결과다. 임베딩 단가가 LLM 대비 작아
     * 감수할 만하지만, 무의미한 질의를 반복하면 그만큼은 새어나간다.
     *
     * <p>{@link #check} 와 나뉘어 있어 동시 요청이 상한을 조금 넘길 수 있다. 쿼터는 인증이
     * 아니라 과금 방어선이고(위 fail-open 과 같은 이유) 정확히 N 회를 보장할 필요가 없다.
     */
    public void consume(String memberId) {
        try {
            String key = key(memberId);
            if (redis.opsForValue().increment(key) == null) {
                return;
            }
            // 만료를 매번 다시 건다. 키에 날짜가 들어 있어 갱신해도 자정까지만 살고,
            // 첫 요청에만 걸면 그때 expire 가 실패한 키는 TTL 없이 영원히 남는다.
            redis.expire(key, Duration.ofSeconds(Math.max(1, secondsUntilMidnight())));
        } catch (DataAccessException e) {
            log.warn("Redis 장애로 일일 쿼터를 기록하지 못했다 — 통과시킨다(fail-open). memberId={}", memberId, e);
        }
    }

    private static String key(String memberId) {
        return "ai:quota:%s:%s".formatted(memberId, LocalDate.now());
    }

    private static long secondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay())
                .toSeconds();
    }
}

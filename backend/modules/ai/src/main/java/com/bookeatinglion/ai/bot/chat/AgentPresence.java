package com.bookeatinglion.ai.bot.chat;

import com.bookeatinglion.ai.bot.config.ChatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 접속 중인 상담사 집계.
 *
 * <p>🔴 <b>평범한 Set 으로 세면 안 된다.</b> {@code SADD}/{@code SREM} 은 정상 종료에만
 * 동작한다. OOMKill·노드 상실·네트워크 분단·노트북 덮개 닫기에서는 종료 콜백이 아예 불리지
 * 않고, 그 상담사는 영원히 "접속 중" 으로 남는다.
 *
 * <p>그 결과가 특히 나쁘다. 죽은 상담사 하나 때문에 인원수가 0이 아니게 되고,
 * <b>"상담사가 없으면 즉시 안내" 규칙이 조용히 무력화된다.</b> 사용자는 안내 대신 아무도
 * 오지 않는 방에서 기다린다. 에러도 로그도 없다.
 *
 * <p>그래서 ZSET 에 마지막 하트비트 시각을 score 로 넣고 <b>범위로 센다.</b>
 * {@code ZCARD} 를 쓰면 자료구조만 바꾸고 문제는 그대로 남는다.
 */
@Component
@RequiredArgsConstructor
public class AgentPresence {

    private static final String KEY = "ai:chat:agents";

    private final StringRedisTemplate redis;
    private final ChatProperties props;

    /** 소켓 연결 시, 그리고 Pong 을 받을 때마다 부른다. */
    public void heartbeat(String agentId) {
        redis.opsForZSet().add(KEY, agentId, System.currentTimeMillis());
    }

    public void leave(String agentId) {
        redis.opsForZSet().remove(KEY, agentId);
    }

    /**
     * 살아 있는 상담사 수.
     *
     * <p>sweeper 는 키 위생용이지 정확성의 근거가 아니다. 파드가 막 떴을 때는 sweeper 가
     * 한 번도 안 돌았을 수 있고, 그때도 이 값이 맞아야 한다. 그래서 매번 범위로 센다.
     */
    public long onlineCount() {
        long floor = System.currentTimeMillis() - props.agentHeartbeatGrace().toMillis();
        Long n = redis.opsForZSet().count(KEY, floor, Double.POSITIVE_INFINITY);
        return n == null ? 0 : n;
    }

    /** 만료된 항목 정리. 없어도 {@link #onlineCount()} 는 정확하다. */
    @Scheduled(fixedDelay = 30_000)
    public void sweep() {
        long floor = System.currentTimeMillis() - props.agentHeartbeatGrace().toMillis();
        redis.opsForZSet().removeRangeByScore(KEY, 0, floor);
    }
}

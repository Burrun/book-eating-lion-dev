package com.bookeatinglion.ai.bot.chat;

import com.bookeatinglion.ai.bot.config.ChatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket 핸드셰이크용 1회성 교환권.
 *
 * <p><b>왜 토큰을 쿼리로 안 보내는가.</b> 브라우저는 {@code new WebSocket()} 에
 * {@code Authorization} 헤더를 붙일 수 없다. 남는 수단은 URL 인데, JWT 를 URL 에 실으면
 * nginx 액세스 로그·브라우저 히스토리·{@code Referer} 에 그대로 남는다. 로그를 볼 수 있는
 * 사람이 곧 그 사용자로 로그인할 수 있다는 뜻이다.
 *
 * <p>그래서 URL 로는 <b>교환권</b>만 보낸다. 수명이 짧고 한 번 쓰면 사라지므로 로그에 남아도
 * 이미 죽은 값이다. 신원 확인 자체는 티켓을 발급하는 평범한 REST 요청에서 끝나므로
 * {@code SecurityUtils} 가 그대로 동작한다 — WebSocket 쪽에서 SecurityContext 가 비는
 * 문제를 우회하는 게 아니라 아예 마주치지 않는다.
 *
 * <p>🔴 <b>{@code DailyQuota} 와 fail 정책이 반대다.</b> 쿼터는 Redis 장애 시 통과시키지만
 * (fail-open) 이건 인증이라 막는다(fail-closed). 둘을 같은 규칙으로 다루면 Redis 가 흔들리는
 * 순간 아무나 남의 상담방에 붙는다.
 */
@Component
@RequiredArgsConstructor
public class ChatTicketStore {

    private static final String KEY = "ai:chat:ticket:%s";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ChatProperties props;

    public String issue(ChatIdentity identity) {
        String ticket = UUID.randomUUID().toString();
        redis.opsForValue().set(KEY.formatted(ticket), write(identity), props.ticketTtl());
        return ticket;
    }

    /**
     * 🔴 <b>조회와 삭제가 원자적이어야 한다.</b> GET 후 DEL 로 나누면 같은 티켓으로 두 소켓이
     * 동시에 붙을 수 있다 — 링크를 복사해 준 사람과 받은 사람이 같은 방을 여는 것이다.
     * {@code getAndDelete()} 는 Redis 6.2+ 의 GETDEL 로 나간다.
     *
     * @return 티켓이 없거나 이미 쓰였으면 null
     */
    public ChatIdentity redeem(String ticket) {
        String json = redis.opsForValue().getAndDelete(KEY.formatted(ticket));
        return json == null ? null : read(json);
    }

    private String write(ChatIdentity identity) {
        try {
            return mapper.writeValueAsString(identity);
        } catch (Exception e) {
            throw new IllegalStateException("티켓 직렬화 실패", e);
        }
    }

    private ChatIdentity read(String json) {
        try {
            return mapper.readValue(json, ChatIdentity.class);
        } catch (Exception e) {
            throw new IllegalStateException("티켓 역직렬화 실패", e);
        }
    }
}

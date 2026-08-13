package com.bookeatinglion.ai.bot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 상담 채팅 조정값. {@code RagProperties} 와 같은 이유로 분리한다 — 운영 중에 흔들어봐야
 * 하는 값과 코드가 같이 묶이면 재배포 없이 못 고친다.
 *
 * @param roomTtl 방·전사의 수명. 지나면 Redis 가 알아서 지운다.
 * @param maxMessages 전사 보관 상한. 없으면 소켓 하나로 Redis 메모리를 채울 수 있다.
 * @param maxMessageChars 한 발화 길이 상한.
 * @param agentHeartbeatGrace 이 시간 동안 하트비트가 없으면 죽은 상담사로 본다.
 *     짧으면 살아 있는 상담사를 죽었다고 보고(→ 앉아 있는데 문의 안내가 나감),
 *     길면 죽은 상담사를 살았다고 봐서 사용자가 헛되이 기다린다.
 */
@ConfigurationProperties(prefix = "app.ai.chat")
public record ChatProperties(
        Duration roomTtl, int maxMessages, int maxMessageChars, Duration ticketTtl, Duration agentHeartbeatGrace) {}

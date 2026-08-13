package com.bookeatinglion.ai.bot.chat;

/**
 * 대화 한 줄. Redis 리스트에 JSON 으로 쌓이고 Pub/Sub 으로도 이 형태 그대로 나간다.
 *
 * <p>{@code seq} 는 Redis {@code INCR} 이 준 값이다. 클라이언트는 이걸로 중복을 제거한다 —
 * 재접속 시 "구독 먼저, 전사 조회 나중" 순서라 겹쳐 받는 구간이 생기는데, 유실보다 중복이
 * 낫기 때문에 일부러 그 순서를 쓴다.
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessage(long seq, ChatRole role, String nickname, String text, String at) {}

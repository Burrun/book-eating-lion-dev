package com.bookeatinglion.ai.api.config;

import com.bookeatinglion.ai.bot.controller.ChatHandshakeInterceptor;
import com.bookeatinglion.ai.bot.controller.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 상담 채팅 WebSocket 배선.
 *
 * <p>경로를 {@code /api/ai/bot/**} 아래에 두지 않고 {@code /ws/} 로 분리한다. WebSocket 은
 * 업그레이드 헤더 통과와 유휴 타임아웃 설정이 HTTP 와 달라 Ingress 규칙을 따로 걸어야
 * 하는데, 한 경로 트리에 섞으면 그 설정이 REST 요청에도 딸려 붙는다.
 *
 * <p>SockJS 폴백을 붙이지 않는다. 붙이면 XHR 스트리밍 경로가 생겨 요청이 여러 개로 쪼개지고,
 * 그때부터 진짜로 sticky session 이 필요해진다. 지금 설계는 Redis Pub/Sub 으로 파드 간
 * 전달을 하므로 어느 파드에 붙든 무관하다 — 그 성질을 폴백 하나로 잃을 이유가 없다.
 *
 * <p>⚠️ Ingress 에 {@code /ws/ai} 경로와 업그레이드 허용, 유휴 타임아웃 상향이 필요하다.
 * nginx 기본값(60초)이면 대화가 잠시 멈춘 사이 연결이 끊긴다. 아직 매니페스트에 없다.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ChatHandshakeInterceptor handshakeInterceptor;

    /**
     * Origin 검증이 이 채널의 유일한 방어선이다 — 브라우저는 WebSocket 에 CORS preflight 를
     * 보내지 않아 Ingress 의 CORS 설정이 적용되지 않는다.
     */
    @Value("${app.ai.chat.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/ai/chat")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins);
    }
}

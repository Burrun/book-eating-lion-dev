package com.bookeatinglion.ai.bot.controller;

import com.bookeatinglion.ai.bot.chat.ChatIdentity;
import com.bookeatinglion.ai.bot.chat.ChatTicketStore;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * WebSocket 핸드셰이크 인증.
 *
 * <p>🔴 <b>여기서 통과시킨 소켓은 이후 아무도 다시 검사하지 않는다.</b> 핸들러 안에는
 * SecurityContext 가 없어서(스레드가 다르다) {@code SecurityUtils} 를 쓸 수 없다. 즉 이
 * 클래스가 이 채널의 유일한 인증 지점이다.
 *
 * <p>세션 attribute 에 넣은 값이 이후 모든 권한 판단의 근거가 된다. 클라이언트가 프레임에
 * 담아 보낸 필드는 신원으로 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ChatHandshakeInterceptor.class);

    private final ChatTicketStore tickets;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Map<String, Object> attributes) {

        String ticket = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("ticket");

        if (ticket == null || ticket.isBlank()) {
            return reject(response, "티켓 없음");
        }

        ChatIdentity identity;
        try {
            identity = tickets.redeem(ticket);
        } catch (DataAccessException e) {
            // 🔴 fail-closed. Redis 장애 시 통과시키면 인증이 통째로 사라진다.
            log.error("티켓 검증 실패 — 핸드셰이크를 거절한다", e);
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }

        if (identity == null) {
            return reject(response, "티켓 만료 또는 이미 사용됨");
        }

        attributes.put(ChatWebSocketHandler.ATTR_IDENTITY, identity);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception exception) {}

    /**
     * 상태 코드를 찍지 않고 false 만 반환하면 Spring 이 403 을 낸다. 401 이어야 프론트가
     * "티켓 재발급 후 재시도"와 "권한 없음"을 구분한다 — 재연결 로직이 여기에 달려 있다.
     */
    private static boolean reject(ServerHttpResponse response, String reason) {
        log.debug("채팅 핸드셰이크 거절: {}", reason);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }
}

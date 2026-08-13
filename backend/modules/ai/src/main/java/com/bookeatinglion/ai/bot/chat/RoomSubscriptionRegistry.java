package com.bookeatinglion.ai.bot.chat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 이 파드가 들고 있는 소켓 목록 + Redis 구독 수명.
 *
 * <p>🔴 <b>구독은 소켓 단위가 아니라 파드 단위다.</b> 같은 방에 회원 소켓과 상담사 소켓이
 * 우연히 같은 파드에 붙으면 구독은 하나여야 한다. 소켓마다 등록하면 같은 메시지가 두 번
 * 배달돼 채팅창에 중복으로 찍힌다.
 *
 * <p>🔴 <b>0↔1 전이가 원자적이어야 한다.</b> {@code compute} 밖에서 판단하면 "붙는 중"과
 * "끊는 중"이 겹칠 때 소켓은 남았는데 구독은 해제된 상태가 만들어진다. 예외가 나지 않는다 —
 * 그 방만 조용히 먹통이 되고 재현이 거의 불가능하다.
 *
 * <p>이 맵이 파드가 들고 있는 유일한 상태이고, 소켓이 죽으면 같이 없어지는 게 맞는 값이라
 * 복제할 이유가 없다. <b>그래서 sticky session 이 필요 없다</b> — 권한적 상태(방·전사·배정)는
 * 전부 Redis 에 있어 어느 파드든 roomId 만 알면 그 방을 서빙할 수 있다.
 */
@Component
public class RoomSubscriptionRegistry {

    private static final Logger log = LoggerFactory.getLogger(RoomSubscriptionRegistry.class);

    private final RedisMessageListenerContainer container;
    private final ChatFanoutListener listener;

    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    public RoomSubscriptionRegistry(RedisMessageListenerContainer container, ChatFanoutListener listener) {
        this.container = container;
        this.listener = listener;
        this.listener.bind(this);
    }

    public void attach(String roomId, WebSocketSession session) {
        rooms.compute(roomId, (id, set) -> {
            if (set == null) {
                set = ConcurrentHashMap.newKeySet();
                container.addMessageListener(listener, new ChannelTopic(ChatRoomStore.channel(id)));
            }
            set.add(session);
            return set;
        });
    }

    public void detach(String roomId, WebSocketSession session) {
        rooms.computeIfPresent(roomId, (id, set) -> {
            set.remove(session);
            if (set.isEmpty()) {
                container.removeMessageListener(listener, new ChannelTopic(ChatRoomStore.channel(id)));
                return null; // 맵에서 제거. 안 하면 빈 Set 이 영원히 쌓인다.
            }
            return set;
        });
    }

    /**
     * 채널로 들어온 메시지를 이 파드의 해당 방 소켓에 뿌린다.
     *
     * <p>Redis 에 실린 본문은 봉투가 없는 알맹이다. 여기서 {@code ApiResponse} 모양으로
     * 감싸 서버가 직접 보내는 프레임과 형태를 맞춘다 — 안 그러면 클라이언트가 두 가지 응답
     * 구조를 구분해 파싱해야 한다.
     *
     * <p>이미 JSON 인 본문을 다시 파싱하지 않고 문자열로 끼워 넣는다. 팬아웃은 방에 붙은
     * 소켓 수만큼 도는 경로라 불필요한 역직렬화를 넣을 자리가 아니다.
     */
    public void broadcast(String roomId, String payload) {
        String envelope = "{\"success\":true,\"message\":\"SUCCESS\",\"data\":" + payload + ",\"error\":null}";
        for (WebSocketSession session : rooms.getOrDefault(roomId, Set.of())) {
            send(session, envelope);
        }
    }

    public static void send(WebSocketSession session, String payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException e) {
            // 소켓 하나가 죽은 것으로 나머지 전송을 멈추지 않는다. 정리는 종료 콜백이 한다.
            log.debug("소켓 전송 실패. session={}", session.getId());
        }
    }

    public Collection<WebSocketSession> allSessions() {
        List<WebSocketSession> all = new ArrayList<>();
        rooms.values().forEach(all::addAll);
        return all;
    }
}

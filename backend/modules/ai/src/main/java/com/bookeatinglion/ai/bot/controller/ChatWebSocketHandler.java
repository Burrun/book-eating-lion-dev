package com.bookeatinglion.ai.bot.controller;

import com.bookeatinglion.ai.bot.chat.AgentPresence;
import com.bookeatinglion.ai.bot.chat.ChatIdentity;
import com.bookeatinglion.ai.bot.chat.ChatRole;
import com.bookeatinglion.ai.bot.chat.ChatRoom;
import com.bookeatinglion.ai.bot.chat.ChatRoomStore;
import com.bookeatinglion.ai.bot.chat.ChatState;
import com.bookeatinglion.ai.bot.chat.RoomSubscriptionRegistry;
import com.bookeatinglion.ai.bot.config.ChatProperties;
import com.bookeatinglion.ai.bot.service.InquiryBotService;
import com.bookeatinglion.common.dto.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 상담 채팅. AI 로 시작해 사용자가 원하면 상담사로 넘어간다.
 *
 * <p>사용자와 상담사가 같은 핸들러를 쓰고 {@link ChatIdentity#agent()} 로 갈린다. 경로를
 * 나누면 두 벌의 연결·구독·종료 처리가 생기는데, 다른 건 받는 프레임 종류뿐이다.
 *
 * <p>🔴 <b>모든 발화는 Redis 를 한 바퀴 돈다.</b> 내 파드에 있는 소켓에도 직접 쓰지 않고
 * 발행 → 구독 → 전달로 간다. 로컬과 원격 경로를 나누면 순서가 갈라져, 파드 A 의 사용자와
 * 파드 B 의 상담사가 서로 다른 순서의 대화를 본다.
 *
 * <p>🔴 <b>소켓이 닫혔다고 방을 종료하지 않는다.</b> 롤링 배포마다 진행 중인 상담이 전부
 * 끊긴다. 구독만 해제하고 방은 TTL 이 걷어간다.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    /** 세션 attribute 키. {@code ChatHandshakeInterceptor} 가 넣는다. */
    public static final String ATTR_IDENTITY = "chat.identity";

    private static final String ATTR_ROOM = "chat.roomId";

    /**
     * 상담사가 맡은 방 id 들. 사용자와 달리 상담사는 방 하나에 묶이지 않아
     * {@link #ATTR_ROOM} 으로는 표현되지 않는다.
     *
     * <p>🔴 이걸 안 들고 있으면 소켓이 끊겨도 {@code onClaim} 이 건 구독이 떨어지지 않는다.
     * 죽은 세션이 레지스트리에 남고 그 방의 Redis 구독도 파드가 죽을 때까지 유지된다.
     */
    private static final String ATTR_CLAIMED = "chat.claimedRooms";

    /** 전송 대기 상한(ms)과 버퍼 상한(byte). 넘으면 세션을 끊는다. */
    private static final int SEND_TIMEOUT_MS = 5_000;

    private static final int SEND_BUFFER_BYTES = 128 * 1024;

    private final ChatRoomStore rooms;
    private final RoomSubscriptionRegistry registry;
    private final AgentPresence presence;
    private final InquiryBotService bot;
    private final ObjectMapper mapper;
    private final ChatProperties props;
    private final ExecutorService botExecutor;

    public ChatWebSocketHandler(
            ChatRoomStore rooms,
            RoomSubscriptionRegistry registry,
            AgentPresence presence,
            InquiryBotService bot,
            ObjectMapper mapper,
            ChatProperties props,
            @Qualifier("botAnswerExecutor") ExecutorService botExecutor) {
        this.rooms = rooms;
        this.registry = registry;
        this.presence = presence;
        this.bot = bot;
        this.mapper = mapper;
        this.props = props;
        this.botExecutor = botExecutor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
        // 🔴 데코레이터를 벗기지 말 것. 이 채널은 Redis 리스너 스레드·봇 워커 스레드·컨테이너
        // 스레드가 같은 세션에 동시에 쓴다. 원본에 직접 쓰면 Tomcat 이
        // TEXT_PARTIAL_WRITING 을 던지고 그 시점부터 그 소켓은 못 쓴다. 부하가 낮으면
        // 거의 안 나고 시연 당일에 난다.
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, SEND_TIMEOUT_MS, SEND_BUFFER_BYTES);
        raw.getAttributes().put("chat.session", session);

        ChatIdentity identity = identity(session);
        if (identity.agent()) {
            presence.heartbeat(identity.memberId());
            registry.attach(ChatRoomStore.AGENT_CHANNEL_ROOM, session);
            send(session, Map.of("type", "AGENT_READY", "waiting", rooms.waitingRooms()));
            return;
        }

        ChatRoom room = rooms.openOrResume(identity);
        session.getAttributes().put(ATTR_ROOM, room.roomId());
        registry.attach(room.roomId(), session);

        // 구독을 먼저, 전사 조회를 나중에. 순서가 반대면 그 사이 발행된 메시지가 유실된다.
        // 이 순서면 겹쳐 받을 수는 있는데, 중복은 seq 로 지울 수 있고 유실은 못 되살린다.
        send(
                session,
                Map.of(
                        "type",
                        "JOINED",
                        "roomId",
                        room.roomId(),
                        "state",
                        room.state(),
                        "messages",
                        rooms.transcript(room.roomId())));
    }

    @Override
    protected void handleTextMessage(WebSocketSession raw, TextMessage message) throws Exception {
        WebSocketSession session = wrapped(raw);
        ChatIdentity identity = identity(session);

        Frame frame;
        try {
            frame = mapper.readValue(message.getPayload(), Frame.class);
        } catch (Exception e) {
            sendError(session, "INVALID_REQUEST", "JSON 형식이 아닙니다.");
            return;
        }

        if (frame.type() == null) {
            sendError(session, "INVALID_REQUEST", "type 이 없습니다.");
            return;
        }

        switch (frame.type()) {
            case "ASK" -> onAsk(session, identity, frame);
            case "SAY" -> onSay(session, identity, frame);
            case "ESCALATE" -> onEscalate(session, identity);
            case "CLAIM" -> onClaim(session, identity, frame);
            case "CLOSE" -> onClose(session, identity, frame);
            default -> sendError(session, "INVALID_REQUEST", "알 수 없는 type: " + frame.type());
        }
    }

    // ── 사용자 ────────────────────────────────────────────────────────────

    /** AI 에게 묻는다. BOT 상태에서만 유효하다 — LIVE 인데 봇이 끼어들면 안 된다. */
    private void onAsk(WebSocketSession session, ChatIdentity identity, Frame frame) {
        String roomId = roomId(session);
        if (!validText(session, frame.text())) {
            return;
        }
        ChatRoom room = rooms.load(roomId);
        if (room == null || room.state() != ChatState.BOT) {
            sendError(session, "INVALID_STATE", "지금은 AI 에게 물을 수 없는 상태입니다.");
            return;
        }

        rooms.append(roomId, ChatRole.USER, identity.nickname(), frame.text());
        answerAsync(session, roomId, frame.text());
    }

    /**
     * 봇 호출을 컨테이너 스레드에서 떼어낸다. Bedrock 왕복이 수 초라 여기서 블록하면
     * 소켓 수만큼 Tomcat 스레드가 묶이고, 그 풀은 HTTP 와 공유라 {@code /actuator/health}
     * 까지 느려진다.
     *
     * <p>{@code @Bulkhead} 는 세마포어 기반이라 스레드가 바뀌어도 그대로 걸린다. 다만
     * <b>반드시 주입된 빈으로</b> 불러야 한다 — 프록시 경유가 격리의 전부다.
     */
    private void answerAsync(WebSocketSession session, String roomId, String question) {
        try {
            botExecutor.execute(() -> {
                try {
                    rooms.append(roomId, ChatRole.BOT, "AI 상담사", bot.answer(question));
                } catch (BulkheadFullException e) {
                    sendError(session, "BUSY", "지금 붐빕니다. 잠시 후 다시 시도해 주세요.");
                } catch (RuntimeException e) {
                    log.error("봇 응답 실패. roomId={}", roomId, e);
                    sendError(session, "BOT_FAILED", "답변을 만들지 못했습니다.");
                }
            });
        } catch (RuntimeException e) {
            sendError(session, "BUSY", "지금 붐빕니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    /**
     * 상담사 연결 요청.
     *
     * <p>접속한 상담사가 없으면 문의 게시판으로 안내하고 끝낸다. 대기열을 만들지 않는 이유는
     * 기다려도 올 사람이 없기 때문이다 — 스피너를 오래 보여주는 것보다 바로 다른 길을
     * 알려주는 게 낫다.
     */
    private void onEscalate(WebSocketSession session, ChatIdentity identity) {
        String roomId = roomId(session);
        ChatRoom room = rooms.load(roomId);
        if (room == null || room.state() != ChatState.BOT) {
            sendError(session, "INVALID_STATE", "이미 상담사 연결을 요청했거나 종료된 상담입니다.");
            return;
        }

        long online = onlineAgents();
        if (online == 0) {
            rooms.append(roomId, ChatRole.SYSTEM, null, "지금은 연결 가능한 상담사가 없습니다. 문의 게시판을 이용해 주세요!");
            rooms.close(room);
            send(session, Map.of("type", "NO_AGENT", "roomId", roomId));
            return;
        }

        rooms.markWaiting(room);
        rooms.append(roomId, ChatRole.SYSTEM, null, "상담사를 연결하는 중입니다.");
    }

    /**
     * Redis 가 흔들리면 "상담사 있음"으로 가정하지 않는다. 그러면 아무도 못 받는 방이 생겨
     * 사용자가 무한히 기다린다. 0명으로 보고 안내하는 쪽이 안전하다 — 최악의 결과가
     * "실시간 상담 대신 게시판 안내"이고, 사용자는 어쨌든 다음 행동을 안다.
     */
    private long onlineAgents() {
        try {
            return presence.onlineCount();
        } catch (RuntimeException e) {
            log.error("상담사 접속 확인 실패 — 0명으로 간주한다", e);
            return 0;
        }
    }

    // ── 공통 / 상담사 ─────────────────────────────────────────────────────

    /** LIVE 구간의 발화. 사용자와 상담사가 같은 경로를 쓴다. */
    private void onSay(WebSocketSession session, ChatIdentity identity, Frame frame) {
        String roomId = identity.agent() ? frame.roomId() : roomId(session);
        if (roomId == null) {
            sendError(session, "INVALID_REQUEST", "roomId 가 없습니다.");
            return;
        }
        if (!validText(session, frame.text())) {
            return;
        }

        ChatRoom room = rooms.load(roomId);
        if (room == null || room.state() != ChatState.LIVE) {
            sendError(session, "INVALID_STATE", "대화 중인 상담이 아닙니다.");
            return;
        }
        if (identity.agent() && !identity.memberId().equals(room.agentId())) {
            sendError(session, "FORBIDDEN", "이 상담을 맡은 상담사가 아닙니다.");
            return;
        }

        rooms.append(roomId, identity.agent() ? ChatRole.AGENT : ChatRole.USER, identity.nickname(), frame.text());
    }

    private void onClaim(WebSocketSession session, ChatIdentity identity, Frame frame) {
        if (!identity.agent()) {
            sendError(session, "FORBIDDEN", "상담사만 받을 수 있습니다.");
            return;
        }
        if (frame.roomId() == null) {
            sendError(session, "INVALID_REQUEST", "roomId 가 없습니다.");
            return;
        }

        if (!rooms.claim(frame.roomId(), identity.memberId(), identity.nickname())) {
            sendError(session, "ALREADY_CLAIMED", "다른 상담사가 먼저 받았거나 종료된 상담입니다.");
            return;
        }

        // 이긴 상담사만 그 방을 구독한다. 이 시점부터 양방향이 성립한다.
        registry.attach(frame.roomId(), session);
        claimedRooms(session).add(frame.roomId());
        send(
                session,
                Map.of(
                        "type", "CLAIMED",
                        "roomId", frame.roomId(),
                        "messages", rooms.transcript(frame.roomId())));
        rooms.append(frame.roomId(), ChatRole.SYSTEM, null, "상담사가 연결되었습니다.");
    }

    private void onClose(WebSocketSession session, ChatIdentity identity, Frame frame) {
        String roomId = identity.agent() ? frame.roomId() : roomId(session);
        ChatRoom room = roomId == null ? null : rooms.load(roomId);
        if (room == null) {
            return;
        }
        rooms.append(roomId, ChatRole.SYSTEM, null, "상담이 종료되었습니다.");
        rooms.close(room);
    }

    // ── 연결 수명 ─────────────────────────────────────────────────────────

    /**
     * 서버가 보낸 Ping 에 대한 응답. 브라우저가 JS 개입 없이 자동으로 돌려준다.
     *
     * <p>클라이언트 {@code setInterval} 로 하트비트를 보내면 <b>백그라운드 탭에서 타이머가
     * 스로틀링</b>되어(크롬은 분당 1회까지) 살아 있는 상담사가 죽은 것으로 처리된다.
     * 프로토콜 레벨 Ping/Pong 은 그 대상이 아니다.
     */
    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        ChatIdentity identity = identity(session);
        if (identity != null && identity.agent()) {
            presence.heartbeat(identity.memberId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
        WebSocketSession session = wrapped(raw);
        ChatIdentity identity = identity(session);

        if (identity != null && identity.agent()) {
            registry.detach(ChatRoomStore.AGENT_CHANNEL_ROOM, session);
            presence.leave(identity.memberId());
            for (String claimed : claimedRooms(session)) {
                registry.detach(claimed, session);
                releaseOrphan(claimed, identity);
            }
        }
        String roomId = roomId(session);
        if (roomId != null) {
            registry.detach(roomId, session);
        }
    }

    /**
     * 상담사가 CLOSE 없이 끊겼을 때 남는 방을 처리한다.
     *
     * <p>그냥 두면 방은 LIVE 인 채로 TTL(24h)까지 남는다. 사용자는 아무도 없는 방에 말을 하고,
     * 상태가 BOT 이 아니라 상담사 재요청도 막힌다 — 나갈 길이 없는 상태다.
     *
     * <p>{@link #onEscalate} 와 같은 규칙을 쓴다. 다른 상담사가 있으면 대기열로 되돌리고,
     * 아무도 없으면 게시판으로 안내하며 닫는다. 여기서 새 정책을 만들지 않는다.
     */
    private void releaseOrphan(String roomId, ChatIdentity identity) {
        ChatRoom room = rooms.load(roomId);
        // 정상 종료였거나 이미 다른 상담사가 이어받았으면 건드릴 것이 없다.
        if (room == null
                || room.state() != ChatState.LIVE
                || !identity.memberId().equals(room.agentId())) {
            return;
        }

        if (onlineAgents() == 0) {
            rooms.append(roomId, ChatRole.SYSTEM, null, "상담사 연결이 끊어졌습니다. 문의 게시판을 이용해 주세요!");
            rooms.close(room);
            return;
        }
        rooms.append(roomId, ChatRole.SYSTEM, null, "상담사 연결이 끊어졌습니다. 다시 연결하는 중입니다.");
        rooms.markWaiting(room);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> claimedRooms(WebSocketSession session) {
        return (Set<String>)
                session.getAttributes().computeIfAbsent(ATTR_CLAIMED, key -> ConcurrentHashMap.newKeySet());
    }

    // ── 보조 ──────────────────────────────────────────────────────────────

    private boolean validText(WebSocketSession session, String text) {
        if (text == null || text.isBlank()) {
            sendError(session, "INVALID_REQUEST", "내용이 비었습니다.");
            return false;
        }
        if (text.length() > props.maxMessageChars()) {
            sendError(session, "INVALID_REQUEST", "내용이 너무 깁니다.");
            return false;
        }
        return true;
    }

    private static ChatIdentity identity(WebSocketSession session) {
        return (ChatIdentity) session.getAttributes().get(ATTR_IDENTITY);
    }

    private static String roomId(WebSocketSession session) {
        return (String) session.getAttributes().get(ATTR_ROOM);
    }

    /** 데코레이터로 감싼 세션을 꺼낸다. 콜백은 원본 세션으로 들어온다. */
    private static WebSocketSession wrapped(WebSocketSession raw) {
        Object decorated = raw.getAttributes().get("chat.session");
        return decorated instanceof WebSocketSession s ? s : raw;
    }

    /**
     * HTTP 와 같은 봉투({@code ApiResponse})를 쓴다. 채널이 달라도 프론트가 응답을 다르게
     * 해석할 이유가 없다.
     *
     * <p>{@code Map.of} 로 직접 만들지 않는다 — null 값을 거부해서 {@code error: null} 을
     * 담는 순간 NPE 가 난다. 실제로 그렇게 짰다가 연결 직후 첫 프레임이 안 나갔다.
     */
    private void send(WebSocketSession session, Object payload) {
        write(session, ApiResponse.success(payload));
    }

    private void sendError(WebSocketSession session, String code, String message) {
        write(session, ApiResponse.error(code, message));
    }

    private void write(WebSocketSession session, ApiResponse<?> response) {
        try {
            RoomSubscriptionRegistry.send(session, mapper.writeValueAsString(response));
        } catch (Exception e) {
            log.error("응답 직렬화 실패", e);
        }
    }

    /** 클라이언트 프레임. 신원 필드는 두지 않는다 — 신원은 핸드셰이크가 정한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Frame(String type, String text, String roomId) {}
}

package com.bookeatinglion.ai.bot.chat;

import com.bookeatinglion.ai.bot.config.ChatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 상담방 상태와 전사(transcript). 전부 Redis 에 있고 DB 에는 아무것도 남기지 않는다.
 *
 * <p>{@code docs/ai-api-plan.md} 가 "상담 로그를 DB 에 안 쓴다 — 구조화 JSON → CloudWatch"
 * 로 정해둔 방침을 그대로 따른다. 진행 중인 대화는 휘발돼도 되는 값이고, 남겨야 할 것은
 * 이미 로그로 나간다.
 *
 * <p>키 규약은 {@code ai:<용도>:<id>} 로 {@code DailyQuota}·{@code PurchasedBookCache} 와 같다.
 */
@Component
public class ChatRoomStore {

    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ChatProperties props;
    private final RedisScript<Long> appendScript;
    private final RedisScript<Long> claimScript;

    public ChatRoomStore(StringRedisTemplate redis, ObjectMapper mapper, ChatProperties props) {
        this.redis = redis;
        this.mapper = mapper;
        this.props = props;
        this.appendScript = script("redis/chat-append.lua");
        this.claimScript = script("redis/chat-claim.lua");
    }

    private static RedisScript<Long> script(String path) {
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setLocation(new ClassPathResource(path));
        s.setResultType(Long.class);
        return s;
    }

    // ── 키 ────────────────────────────────────────────────────────────────

    public static String channel(String roomId) {
        return "ai:chat:ch:" + roomId;
    }

    /**
     * 상담사 콘솔이 구독하는 채널. 새 대기방·배정 완료 알림이 여기로 간다.
     *
     * <p>구독 레지스트리는 방을 id 로 다루므로 상담사 채널도 방 하나처럼 취급한다 —
     * 그래야 구독/해제 경로가 한 벌로 유지된다.
     */
    public static final String AGENT_CHANNEL_ROOM = "agents";

    public static final String AGENT_CHANNEL = channel(AGENT_CHANNEL_ROOM);

    private static final String WAITING_ZSET = "ai:chat:waiting";

    private static String roomKey(String roomId) {
        return "ai:chat:room:" + roomId;
    }

    private static String msgKey(String roomId) {
        return "ai:chat:msg:" + roomId;
    }

    private static String seqKey(String roomId) {
        return "ai:chat:seq:" + roomId;
    }

    private static String memberKey(String memberId) {
        return "ai:chat:member:" + memberId;
    }

    // ── 방 ────────────────────────────────────────────────────────────────

    /**
     * 진행 중인 방을 찾고, 없으면 만든다.
     *
     * <p>1인 1방을 강제한다. 탭을 두 개 열면 같은 방에 소켓 두 개가 붙어 대화록이 일치한다.
     * 방이 둘 생기면 상담사 콘솔에 같은 사람이 두 번 뜨고 하나는 영원히 방치된다.
     *
     * <p>roomId 에 memberId 를 쓰지 않는다 — Pub/Sub 채널명에 들어가는 값이라
     * {@code MONITOR} 나 로그에 사용자 식별자가 그대로 노출된다.
     */
    public ChatRoom openOrResume(ChatIdentity identity) {
        String existing = redis.opsForValue().get(memberKey(identity.memberId()));
        if (existing != null) {
            ChatRoom room = load(existing);
            if (room != null && room.state() != ChatState.CLOSED) {
                return room;
            }
        }

        String roomId = UUID.randomUUID().toString();
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("state", ChatState.BOT.name());
        hash.put("memberId", identity.memberId());
        hash.put("nickname", identity.nickname() == null ? "" : identity.nickname());
        hash.put("createdAt", String.valueOf(System.currentTimeMillis()));

        redis.opsForHash().putAll(roomKey(roomId), hash);
        redis.expire(roomKey(roomId), props.roomTtl());
        redis.opsForValue().set(memberKey(identity.memberId()), roomId, props.roomTtl());

        return new ChatRoom(roomId, ChatState.BOT, identity.memberId(), identity.nickname(), null, null);
    }

    public ChatRoom load(String roomId) {
        Map<Object, Object> hash = redis.opsForHash().entries(roomKey(roomId));
        if (hash.isEmpty()) {
            return null;
        }
        return new ChatRoom(
                roomId,
                ChatState.valueOf(str(hash, "state")),
                str(hash, "memberId"),
                str(hash, "nickname"),
                str(hash, "agentId"),
                str(hash, "agentNickname"));
    }

    private static String str(Map<Object, Object> hash, String field) {
        Object v = hash.get(field);
        return v == null || v.toString().isEmpty() ? null : v.toString();
    }

    // ── 전사 ──────────────────────────────────────────────────────────────

    /**
     * 대화 한 줄을 적재하고 같은 스크립트 안에서 발행한다.
     *
     * <p>🔴 <b>발행한 파드도 자기 구독으로 되받는다.</b> 로컬 전송과 원격 전송을 나누면 두
     * 경로의 순서가 갈라져, 파드 A 의 사용자와 파드 B 의 상담사가 서로 다른 순서의 대화록을
     * 본다. 전송 경로는 하나여야 한다 — 지연 1ms 를 주고 일관성을 산다.
     *
     * @return 부여된 seq. 방이 이미 없으면 -1.
     */
    public long append(String roomId, ChatRole role, String nickname, String text) {
        Long seq = redis.execute(
                appendScript,
                List.of(seqKey(roomId), msgKey(roomId), roomKey(roomId)),
                role.name(),
                nickname == null ? "" : nickname,
                text,
                LocalDateTime.now().format(AT),
                channel(roomId),
                String.valueOf(props.roomTtl().toSeconds()),
                String.valueOf(props.maxMessages()));
        return seq == null ? -1 : seq;
    }

    /** 재접속·상담사 합류 시 지금까지의 대화를 그대로 돌려준다. */
    public List<ChatMessage> transcript(String roomId) {
        List<String> raw = redis.opsForList().range(msgKey(roomId), 0, -1);
        if (raw == null) {
            return List.of();
        }
        List<ChatMessage> messages = new ArrayList<>(raw.size());
        for (String json : raw) {
            try {
                messages.add(mapper.readValue(json, ChatMessage.class));
            } catch (Exception e) {
                // 한 줄이 깨졌다고 대화 전체를 못 보여주면 안 된다.
                continue;
            }
        }
        return messages;
    }

    // ── 상태 전이 ─────────────────────────────────────────────────────────

    /** 상담사를 기다리는 상태로. 상담사 콘솔에 알린다. */
    public void markWaiting(ChatRoom room) {
        redis.opsForHash().put(roomKey(room.roomId()), "state", ChatState.WAITING.name());
        redis.opsForZSet().add(WAITING_ZSET, room.roomId(), System.currentTimeMillis());
        publishToAgents(room.roomId(), "ROOM_WAITING", room.nickname());
    }

    /**
     * 상담사 배정. 원자적이라 동시에 눌러도 하나만 이긴다.
     *
     * @return 이겼으면 true
     */
    public boolean claim(String roomId, String agentId, String agentNickname) {
        Long r = redis.execute(
                claimScript,
                List.of(roomKey(roomId), WAITING_ZSET),
                agentId,
                agentNickname == null ? "" : agentNickname,
                roomId,
                String.valueOf(System.currentTimeMillis()));
        boolean won = r != null && r == 1L;
        if (won) {
            publishToAgents(roomId, "ROOM_CLAIMED", agentNickname);
        }
        return won;
    }

    /**
     * 종료. 방 해시는 지우지 않고 상태만 바꾼다 — 재접속한 클라이언트가 "없는 방"(→ 새 방
     * 생성 → 대화록 소실)이 아니라 "끝난 방"을 봐야 한다. TTL 이 알아서 걷어간다.
     */
    public void close(ChatRoom room) {
        redis.opsForHash().put(roomKey(room.roomId()), "state", ChatState.CLOSED.name());
        redis.opsForZSet().remove(WAITING_ZSET, room.roomId());
        redis.delete(memberKey(room.memberId()));
        publishToAgents(room.roomId(), "ROOM_GONE", null);
    }

    /** 대기 중인 방 목록. 상담사 콘솔이 처음 붙을 때 스냅샷으로 받는다. */
    public List<ChatRoom> waitingRooms() {
        var ids = redis.opsForZSet().range(WAITING_ZSET, 0, -1);
        if (ids == null) {
            return List.of();
        }
        List<ChatRoom> rooms = new ArrayList<>();
        for (String id : ids) {
            ChatRoom room = load(id);
            if (room != null && room.state() == ChatState.WAITING) {
                rooms.add(room);
            }
        }
        return rooms;
    }

    private void publishToAgents(String roomId, String type, String nickname) {
        try {
            redis.convertAndSend(
                    AGENT_CHANNEL,
                    mapper.writeValueAsString(
                            Map.of("type", type, "roomId", roomId, "nickname", nickname == null ? "" : nickname)));
        } catch (Exception e) {
            throw new IllegalStateException("상담사 채널 발행 실패", e);
        }
    }
}

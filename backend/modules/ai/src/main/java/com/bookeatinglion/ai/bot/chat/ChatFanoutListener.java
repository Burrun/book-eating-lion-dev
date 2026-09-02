package com.bookeatinglion.ai.bot.chat;

import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis 채널 → 이 파드의 소켓.
 *
 * 어느 방인지는 채널명으로 판별한다. 메시지 본문에 roomId 를 넣고 그걸 믿으면,
 * 다른 방 채널로 들어온 메시지를 엉뚱한 방에 뿌릴 수 있다.
 *
 * {@link RoomSubscriptionRegistry} 와 서로를 참조해야 하는데(구독 등록 ↔ 수신 전달)
 * 생성자 순환이 되므로 레지스트리가 자기를 주입한다.
 */
@Component
public class ChatFanoutListener implements MessageListener {

    private static final String CHANNEL_PREFIX = "ai:chat:ch:";

    private RoomSubscriptionRegistry registry;

    void bind(RoomSubscriptionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        if (!channel.startsWith(CHANNEL_PREFIX)) {
            return;
        }
        registry.broadcast(channel.substring(CHANNEL_PREFIX.length()), body);
    }
}

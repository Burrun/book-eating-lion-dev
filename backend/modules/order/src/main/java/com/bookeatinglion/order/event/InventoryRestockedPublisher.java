package com.bookeatinglion.order.event;

import com.bookeatinglion.common.event.InventoryRestockedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryRestockedPublisher {

    private final StringRedisTemplate redisTemplate;

    public void publish(InventoryRestockedEvent event) {
        redisTemplate
                .opsForStream()
                .add(StreamRecords.mapBacked(event.toMap()).withStreamKey(InventoryRestockedEvent.STREAM_KEY));
        log.info("InventoryRestockedEvent 발행: eventId={}, bookId={}", event.eventId(), event.bookId());
    }
}

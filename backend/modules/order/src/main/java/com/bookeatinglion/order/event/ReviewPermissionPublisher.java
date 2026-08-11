package com.bookeatinglion.order.event;

import com.bookeatinglion.common.event.ReviewPermissionGranted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 구매 확정 시 리뷰 권한을 catalog-service 로 넘긴다.
 *
 * 기술 스택 문서 §3 에 Redis Streams 가 "MSA 간 비동기 이벤트 대기열"로 이미
 * 명시돼 있어 신규 기술 도입이 아니다. Kafka 를 새로 들이지 않은 이유이기도 하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewPermissionPublisher {

    private final StringRedisTemplate redisTemplate;

    public void publish(ReviewPermissionGranted event) {
        redisTemplate
                .opsForStream()
                .add(StreamRecords.mapBacked(event.toMap()).withStreamKey(ReviewPermissionGranted.STREAM_KEY));

        log.info("ReviewPermissionGranted 발행: memberId={}, orderItemId={}", event.memberId(), event.orderItemId());
    }
}

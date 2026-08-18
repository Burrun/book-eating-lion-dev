package com.bookeatinglion.catalog.api.config;

import com.bookeatinglion.book.event.ReviewPermissionConsumer;
import com.bookeatinglion.common.event.ReviewPermissionGranted;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

/**
 * Redis Streams 소비자 배선. 컨슈머 그룹을 쓰는 이유는 catalog Pod 가 2~20개로
 * 확장돼도 이벤트가 한 번만 처리되게 하기 위해서다.
 */
@Slf4j
@Configuration
public class ReviewPermissionStreamConfig {

    private static final String GROUP = "catalog-service";

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> reviewPermissionContainer(
            RedisConnectionFactory connectionFactory) {

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(1))
                .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    @Bean
    public StreamSubscriptionLifecycle reviewPermissionSubscription(
            @Qualifier("reviewPermissionContainer")
                    StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
            StringRedisTemplate redisTemplate,
            ReviewPermissionConsumer consumer) {
        return new StreamSubscriptionLifecycle(container, redisTemplate, consumer);
    }

    /** 컨슈머 그룹 생성은 멱등하지 않으므로(BUSYGROUP) 기동 시 한 번만, 예외를 삼키고 만든다. */
    static class StreamSubscriptionLifecycle implements InitializingBean, DisposableBean {

        private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
        private final StringRedisTemplate redisTemplate;
        private final ReviewPermissionConsumer consumer;
        private Subscription subscription;

        StreamSubscriptionLifecycle(
                StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
                StringRedisTemplate redisTemplate,
                ReviewPermissionConsumer consumer) {
            this.container = container;
            this.redisTemplate = redisTemplate;
            this.consumer = consumer;
        }

        @Override
        public void afterPropertiesSet() {
            try {
                redisTemplate
                        .opsForStream()
                        .createGroup(ReviewPermissionGranted.STREAM_KEY, ReadOffset.from("0"), GROUP);
            } catch (Exception e) {
                log.debug("컨슈머 그룹이 이미 존재합니다: {}", GROUP);
            }

            subscription = container.receiveAutoAck(
                    Consumer.from(GROUP, "catalog-" + System.getenv().getOrDefault("HOSTNAME", "local")),
                    StreamOffset.create(ReviewPermissionGranted.STREAM_KEY, ReadOffset.lastConsumed()),
                    consumer);

            container.start();
        }

        @Override
        public void destroy() {
            if (subscription != null) {
                subscription.cancel();
            }
            container.stop();
        }
    }
}

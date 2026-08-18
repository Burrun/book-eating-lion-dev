package com.bookeatinglion.catalog.api.config;

import com.bookeatinglion.book.event.InventoryRestockedConsumer;
import com.bookeatinglion.common.event.InventoryRestockedEvent;
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

@Slf4j
@Configuration
public class InventoryRestockedStreamConfig {
    private static final String GROUP = "catalog-restock-alerts";

    @Bean
    StreamMessageListenerContainer<String, MapRecord<String, String, String>> inventoryRestockedContainer(
            RedisConnectionFactory connectionFactory) {
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(1))
                .build();
        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    @Bean
    InventoryRestockedSubscription inventoryRestockedSubscription(
            @Qualifier("inventoryRestockedContainer")
                    StreamMessageListenerContainer<String, MapRecord<String, String, String>>
                            inventoryRestockedContainer,
            StringRedisTemplate redisTemplate,
            InventoryRestockedConsumer consumer) {
        return new InventoryRestockedSubscription(inventoryRestockedContainer, redisTemplate, consumer);
    }

    static class InventoryRestockedSubscription implements InitializingBean, DisposableBean {
        private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
        private final StringRedisTemplate redisTemplate;
        private final InventoryRestockedConsumer consumer;
        private Subscription subscription;

        InventoryRestockedSubscription(
                StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
                StringRedisTemplate redisTemplate,
                InventoryRestockedConsumer consumer) {
            this.container = container;
            this.redisTemplate = redisTemplate;
            this.consumer = consumer;
        }

        @Override
        public void afterPropertiesSet() {
            try {
                redisTemplate
                        .opsForStream()
                        .createGroup(InventoryRestockedEvent.STREAM_KEY, ReadOffset.from("0"), GROUP);
            } catch (Exception e) {
                log.debug("재입고 이벤트 컨슈머 그룹이 이미 존재합니다: {}", GROUP);
            }
            subscription = container.receiveAutoAck(
                    Consumer.from(GROUP, "catalog-" + System.getenv().getOrDefault("HOSTNAME", "local")),
                    StreamOffset.create(InventoryRestockedEvent.STREAM_KEY, ReadOffset.lastConsumed()),
                    consumer);
            container.start();
        }

        @Override
        public void destroy() {
            if (subscription != null) subscription.cancel();
            container.stop();
        }
    }
}

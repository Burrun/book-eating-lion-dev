package com.bookeatinglion.ai.api.config;

import com.bookeatinglion.ai.recommendation.service.RecommendationIndexingService;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
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

/** Catalog 도서 변경을 받아 추천용 S3 Vector 인덱스를 비동기로 동기화한다. */
@Slf4j
@Configuration
public class RecommendationBookStreamConfig {

    private static final String STREAM = "events:catalog:recommendation-books";
    private static final String GROUP = "ai-recommendation-indexer";

    @Bean
    StreamMessageListenerContainer<String, MapRecord<String, String, String>> recommendationBookContainer(
            RedisConnectionFactory connectionFactory) {
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(1))
                .build();
        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    @Bean
    RecommendationBookSubscription recommendationBookSubscription(
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> recommendationBookContainer,
            StringRedisTemplate redis,
            RecommendationIndexingService indexingService) {
        return new RecommendationBookSubscription(recommendationBookContainer, redis, indexingService);
    }

    static class RecommendationBookSubscription implements InitializingBean, DisposableBean {
        private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
        private final StringRedisTemplate redis;
        private final RecommendationIndexingService indexingService;
        private Subscription subscription;

        RecommendationBookSubscription(
                StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
                StringRedisTemplate redis,
                RecommendationIndexingService indexingService) {
            this.container = container;
            this.redis = redis;
            this.indexingService = indexingService;
        }

        @Override
        public void afterPropertiesSet() {
            try {
                redis.opsForStream().createGroup(STREAM, ReadOffset.from("0"), GROUP);
            } catch (RuntimeException e) {
                log.debug("추천 인덱스 컨슈머 그룹이 이미 존재합니다: {}", GROUP);
            }
            String consumerName = "ai-" + System.getenv().getOrDefault("HOSTNAME", "local");
            subscription = container.receive(
                    Consumer.from(GROUP, consumerName),
                    StreamOffset.create(STREAM, ReadOffset.lastConsumed()),
                    this::consume);
            container.start();
        }

        private void consume(MapRecord<String, String, String> record) {
            Map<String, String> value = record.getValue();
            try {
                long bookId = Long.parseLong(value.get("bookId"));
                if ("DELETE".equals(value.get("action"))) {
                    indexingService.delete(bookId);
                } else {
                    indexingService.upsert(
                            bookId,
                            value.getOrDefault("title", ""),
                            value.getOrDefault("author", ""),
                            value.getOrDefault("category", ""),
                            value.getOrDefault("description", ""));
                }
                redis.opsForStream().acknowledge(STREAM, GROUP, record.getId());
            } catch (RuntimeException e) {
                // ACK하지 않으면 pending에 남는다. 운영 재처리/관측 대상으로 보존한다.
                log.error("추천 도서 인덱싱 실패. recordId={}", record.getId(), e);
            }
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

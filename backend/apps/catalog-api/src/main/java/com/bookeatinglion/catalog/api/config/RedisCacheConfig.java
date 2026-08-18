package com.bookeatinglion.catalog.api.config;

import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 베스트셀러처럼 반복 조회되는 Read-Heavy 목록을 캐싱하기 위한 Redis 기반 CacheManager.
 *
 * TTL 은 캐시별로 분리한다 — "bestsellers" 전용 TTL(30분)이 baseConfig() 를 타고 향후 추가될
 * 다른 캐시의 기본값까지 되어버리지 않도록, 공통 설정(키 직렬화 등)과 TTL 을 분리해서
 * 캐시마다 필요한 값만 얹는다. bestsellers 는 판매량 변경 시 즉시 무효화하는 @CacheEvict 를
 * 두지 않고 TTL 만료로만 신선도를 관리한다 — 실시간 정합성이 아니라 DB Offloading 이
 * 목적이라 최대 30분의 지연은 허용 범위다.
 *
 * 직렬화는 애플리케이션에 이미 등록된(Spring Boot 자동구성) ObjectMapper 빈을 그대로
 * 주입받아 쓴다 — 캐시 직렬화만 별도 ObjectMapper 를 새로 만들면 전역에 등록된 모듈(Java
 * time 등)이나 네이밍 전략이 API 응답 직렬화와 어긋날 수 있다.
 *
 * "bestsellers" 캐시는 정확한 타입(List&lt;BookSummaryResponse&gt;)을 아는 채로 캐싱하므로
 * 타입이 확정된 Jackson2JsonRedisSerializer 를 쓴다 — 범용 GenericJackson2JsonRedisSerializer
 * 는 이 조합(원소 타입은 final record라 개별 타입 태그가 붙지만, List 컨테이너 자체는 타입
 * 래핑이 안 되는 비대칭)에서 저장은 되지만 읽을 때
 * "Unexpected token (START_OBJECT), expected VALUE_STRING" 로 역직렬화가 깨지는 걸 실측으로
 * 확인했다(로컬에서 Redis 붙여 재현). 타입을 고정해 캐싱하면 이 문제가 원천적으로 없다.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /** bestsellers 이외의 캐시가 나중에 추가될 때를 위한 안전망 기본값 — bestsellers 와는 무관하다. */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private static final Duration BESTSELLERS_TTL = Duration.ofMinutes(30);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()));

        RedisCacheConfiguration defaultConfig = base.entryTtl(DEFAULT_TTL)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(objectMapper)));

        RedisCacheConfiguration bestsellersConfig = base.entryTtl(BESTSELLERS_TTL)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        bestsellersSerializer(objectMapper)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(Map.of("bestsellers", bestsellersConfig))
                .build();
    }

    private static Jackson2JsonRedisSerializer<List<BookSummaryResponse>> bestsellersSerializer(
            ObjectMapper objectMapper) {
        var listType = TypeFactory.defaultInstance().constructCollectionType(List.class, BookSummaryResponse.class);
        return new Jackson2JsonRedisSerializer<>(objectMapper, listType);
    }
}

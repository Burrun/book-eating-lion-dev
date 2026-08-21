package com.bookeatinglion.order.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 재고 차감 분산 락(InventoryLockExecutor) 전용. RedisConfig(common) 의 RedisTemplate 은
 * 이벤트 채널/캐시용이고 이건 락 전용 클라이언트라 분리했다 — 용도가 다르면 커넥션도 분리한다.
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    // spring.data.redis.ssl.enabled(application-redis-prod.yml)를 그대로 재사용한다.
    // Spring Data Redis(Lettuce)와 달리 Redisson은 이 프로퍼티를 자동으로 안 읽고
    // "redis://" vs "rediss://" 스킴으로 직접 선택해야 한다 - ElastiCache가
    // transit_encryption_enabled=true(TLS 강제)인데 여기서 평문 "redis://"로
    // 고정돼 있어서, PING 커맨드가 응답 없이 타임아웃되며 order-api 기동이
    // 영원히 멎는 문제가 있었다(2026-08-21 실제로 겪음 - catalog/ai는 Lettuce라
    // ssl.enabled 하나로 해결됐는데 order만 Redisson이라 별도로 안 먹혔음).
    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String scheme = sslEnabled ? "rediss://" : "redis://";
        config.useSingleServer().setAddress(scheme + host + ":" + port);
        return Redisson.create(config);
    }
}

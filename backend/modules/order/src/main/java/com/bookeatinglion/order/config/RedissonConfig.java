package com.bookeatinglion.order.config;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    // spring.data.redis.ssl.enabled(application-redis-prod.yml) 값을 그대로 재사용한다.
    // Spring Data Redis(Lettuce)와 달리 Redisson은 이 프로퍼티를 자동으로 읽지 않으므로,
    // 여기서 "redis://" vs "rediss://" 스킴을 명시적으로 선택해 TLS 사용 여부를 제어한다.
    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        if (!sslEnabled) {
            log.warn("Redisson이 TLS 없이(redis://) {}에 연결합니다 - TLS를 강제하는 Redis 서버라면 "
                    + "연결이 조용히 멎을 수 있습니다. spring.data.redis.ssl.enabled 설정을 확인하세요.", host);
        }
        Config config = new Config();
        String scheme = sslEnabled ? "rediss://" : "redis://";
        config.useSingleServer().setAddress(scheme + host + ":" + port);
        return Redisson.create(config);
    }
}

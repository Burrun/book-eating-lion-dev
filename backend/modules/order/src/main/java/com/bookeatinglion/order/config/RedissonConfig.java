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

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + host + ":" + port);
        return Redisson.create(config);
    }
}

package com.bookeatinglion.ai.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 파드 간 채팅 팬아웃.
 *
 * <p>🔴 <b>Redis Streams 를 쓰지 않는 이유.</b> 이 리포의 기존 비동기 수단인 Streams +
 * 컨슈머 그룹은 "여러 파드 중 정확히 하나만 처리"가 목적이다. 채팅은 정반대로 "그 방의
 * 소켓을 들고 있는 모든 파드가 받아야" 한다. 컨슈머 그룹을 쓰면 메시지가 엉뚱한 파드
 * 하나에 배달되고, 그 파드는 해당 방 소켓이 없어서 조용히 버린다 — 에러도 로그도 없이
 * "가끔 메시지가 안 감"으로만 나타난다.
 *
 * <p>⚠️ Pub/Sub 은 at-most-once 다. 구독이 성립하기 전에 발행된 메시지는 사라진다.
 * 그래서 소켓이 붙을 때 <b>구독을 먼저, 전사 조회를 나중에</b> 한다 — 그러면 겹쳐 받을 수는
 * 있어도 유실은 없고, 중복은 seq 로 지운다.
 */
@Configuration
public class ChatPubSubConfig {

    @Bean(destroyMethod = "destroy")
    public RedisMessageListenerContainer chatListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(chatListenerExecutor());
        // addMessageListener 가 SUBSCRIBE 왕복을 기다리는 상한. 초과하면 예외가 아니라
        // "구독됐다고 믿는" 상태가 되므로 넉넉히 준다.
        container.setMaxSubscriptionRegistrationWaitingTime(3000);
        return container;
    }

    @Bean
    public ThreadPoolTaskExecutor chatListenerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 기본값(SimpleAsyncTaskExecutor)은 메시지마다 스레드를 새로 만든다.
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("chat-sub-");
        return executor;
    }
}

package com.bookeatinglion.ai.api.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 봇 답변 전용 스레드풀.
 *
 * <p>WebSocket 컨테이너 스레드에서 Bedrock 을 직접 부르면 소켓 수만큼 스레드가 묶이고,
 * 그 풀은 HTTP 와 공유라 {@code /actuator/health} 까지 느려진다.
 */
@Configuration
public class BotAnswerExecutorConfig {

    /** aiExternalApi Bulkhead(20)와 맞춘다. 더 크면 큐 대신 Bulkhead 대기가 터지고, 더 작으면 슬롯이 남는다. */
    private static final int POOL_SIZE = 20;

    private static final int QUEUE_CAPACITY = 50;

    @Bean(name = "botAnswerExecutor", destroyMethod = "shutdownNow")
    public ExecutorService botAnswerExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                POOL_SIZE, POOL_SIZE, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY), r -> {
                    Thread t = new Thread(r, "bot-answer");
                    t.setDaemon(true);
                    return t;
                });
        // 큐까지 차면 거절한다. CallerRunsPolicy 로 두면 컨테이너 스레드가 다시 블록되어
        // 이 풀을 둔 목적이 사라진다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}

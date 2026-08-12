package com.bookeatinglion.ai.api.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ai-service 의 외부 의존(Bedrock, S3 Vectors) 설정.
 *
 * <p>인덱스명·버킷명을 상수가 아니라 설정으로 두는 이유는 v2 컷오버와 롤백 때문이다.
 * 재인제스트한 `wiki-v2` 로 옮기고 문제가 생기면 되돌리는 게 설정 한 줄이어야 한다.
 *
 * <p>{@code clients} 는 {@code bedrock} 또는 {@code stub} 이다. 스텁 판별에
 * {@code @ConditionalOnMissingBean} 을 쓰지 않는다 — 자동설정 클래스 밖에서는
 * 빈 등록 순서에 따라 둘 다 뜨거나 랜덤해진다.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String clients, Bedrock bedrock, Vector vector) {

    // apiCallTimeout 이 실제 방어선이다 — resilience4j 의 timelimiter 는
    // @TimeLimiter + CompletableFuture 반환일 때만 동작해서 동기 호출에는 걸리지 않는다.
    // apiCallAttemptTimeout 이 없으면 재시도 한 번의 지연이 전체 예산을 다 먹는다.
    public record Bedrock(
            String region,
            String embeddingModel,
            int embeddingDimension,
            String llmModel,
            int llmMaxTokens,
            double llmTemperature,
            Duration apiCallTimeout,
            Duration apiCallAttemptTimeout) {}

    // nonFilterableMetadataKeys 는 인덱스 생성 시 선언한 값이다. 기본값이 "전부 필터 가능"이라
    // text 를 빠뜨리면 청크 원문이 필터 예산 2KB 에 잡혀 PutVectors 가 400 이다.
    // 생성 후 변경 불가라 기동 시 GetIndex 로 대조한다.
    public record Vector(
            String bucketName, String indexName, String distanceMetric, List<String> nonFilterableMetadataKeys) {}
}

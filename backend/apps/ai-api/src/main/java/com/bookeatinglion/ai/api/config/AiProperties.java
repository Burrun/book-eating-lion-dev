package com.bookeatinglion.ai.api.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ai-service 의 외부 의존(Bedrock, S3 Vectors) 설정.
 *
 * <p>
 * 인덱스명·버킷명을 상수가 아니라 설정으로 두는 이유는 v2 컷오버와 롤백 때문이다.
 * 재인제스트한 `wiki-v2` 로 옮기고 문제가 생기면 되돌리는 게 설정 한 줄이어야 한다.
 *
 * <p>
 * {@code clients} 의 기본값은 {@code bedrock} 이다.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String clients, Bedrock bedrock, Vector vector, Ingest ingest, Sqs sqs) {

    /**
     * EPUB 원본 버킷. 벡터 버킷과 다르다 — 원본은 관리자가 올리는 파일이고 벡터는
     * 우리가 만든 파생물이라 수명도 권한도 다르다.
     */
    public record Ingest(String bucketName) {}

    /**
     * 자동 인제스트 큐. {@code enabled=false} 면 리스너 빈 자체를 만들지 않는다 —
     * 로컬에서 큐를 폴링하며 비용을 태우지 않기 위해서다.
     *
     * <p>{@code waitTimeSeconds} 는 롱폴링이다. 0 이면 빈 응답을 초당 수십 번 받아
     * 요청 과금만 늘어난다.
     */
    public record Sqs(boolean enabled, String queueUrl, int waitTimeSeconds, int maxMessages, Purchase purchase) {}

    /**
     * 구매 이벤트 큐. order-service 가 결제 완료 시 발행한다.
     *
     * <p>인제스트 큐와 {@code waitTimeSeconds} 는 공유하지만 URL 과 on/off 는 따로다 —
     * 한쪽이 죽었다고 다른 쪽까지 멈출 이유가 없고, 로컬에서 구매만 켜고 인제스트는
     * 꺼두는 조합이 흔하다.
     */
    public record Purchase(boolean enabled, String queueUrl) {}

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
    // recommendationIndexName 은 편의 생성자로 기본값을 주지 않는다 — record 에 생성자가
    // 2개 이상이면 @ConfigurationProperties 생성자 바인딩이 어느 걸 써야 할지 못 정해서
    // 아예 바인딩을 포기하고(Vector 필드 전체가 null) 값이 다 있어도 조용히 실패한다
    // (실측: 스프링 Binder 로 재현해 bound=false 확인). 기본값은 application.yml 의
    // ${AI_RECOMMENDATION_VECTOR_INDEX:recommendation-books-v1} 로 이미 주고 있으므로
    // Java 쪽 기본값은 중복이었다.
    public record Vector(
            String bucketName,
            String indexName,
            String recommendationIndexName,
            String distanceMetric,
            List<String> nonFilterableMetadataKeys) {}
}

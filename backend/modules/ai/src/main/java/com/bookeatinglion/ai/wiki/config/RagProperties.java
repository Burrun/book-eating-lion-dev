package com.bookeatinglion.ai.wiki.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 파이프라인의 조정값. Bedrock/S3 Vectors 설정({@code AiProperties})과 달리 여기 있는
 * 이유는 파이프라인이 modules/ai 소유이기 때문이다 — 도메인이 apps 를 의존할 수는 없다.
 *
 * <p>{@code maxDistance} 는 자료마다 달라서 상수로 둘 수 없다. golden set 의 거리 분포를
 * 보고 정하는 값이고, 그전까지는 설정으로 흔들어 볼 수 있어야 한다.
 */
@ConfigurationProperties(prefix = "app.ai.rag")
public record RagProperties(
        int searchTopK,
        int maxCitationsPerBook,
        double maxDistance,
        int maxContextBytes,
        int freeDailyQuota,
        int subscribedDailyQuota) {}

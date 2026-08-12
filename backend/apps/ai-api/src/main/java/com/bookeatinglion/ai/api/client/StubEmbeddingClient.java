package com.bookeatinglion.ai.api.client;

import com.bookeatinglion.ai.client.EmbeddingClient;
import java.util.Random;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AWS 자격증명 없이 로컬을 띄우기 위한 스텁. 차원(1024)이 실제와 같아서 인덱스와
 * 파이프라인 코드는 그대로 쓸 수 있다 — 다만 값이 난수라 검색 결과는 의미가 없다.
 *
 * <p>`@ConditionalOnMissingBean` 이 아니라 `@ConditionalOnProperty` 다. 전자는 자동설정
 * 클래스 밖에서 보장되지 않아서, Bedrock 빈이 늘면 스캔 순서에 따라 둘 다 뜨거나 랜덤해진다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.clients", havingValue = "stub", matchIfMissing = true)
public class StubEmbeddingClient implements EmbeddingClient {

    private final Random random = new Random(42);

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            vector[i] = random.nextFloat();
        }
        return vector;
    }
}

package com.bookeatinglion.ai.api.client;

import com.bookeatinglion.ai.wiki.port.VectorSearchPort;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AWS 자격증명 없이 로컬을 띄우기 위한 스텁. 판별 방식은 {@link StubEmbeddingClient} 와 같다.
 *
 * <p>가짜 인용을 만들지 않고 빈 결과를 준다. 로컬에는 인덱스가 없으니 정직한 답은
 * "근거 없음"(grounded=false)이고, 여기서 그럴듯한 값을 지어내면 파이프라인의 거리 가드가
 * 도는지 안 도는지 로컬에서 영영 알 수 없다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.clients", havingValue = "stub", matchIfMissing = true)
public class StubVectorSearchAdapter implements VectorSearchPort {

    private static final Logger log = LoggerFactory.getLogger(StubVectorSearchAdapter.class);

    @Override
    public List<Match> search(float[] queryVector, Collection<Long> allowedBookIds, int topK) {
        log.debug("스텁 벡터 검색 — 항상 빈 결과. 실제 검색은 app.ai.clients=bedrock 에서 돈다.");
        return List.of();
    }
}

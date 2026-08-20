package com.bookeatinglion.ai.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.Index;
import software.amazon.awssdk.services.s3vectors.model.NotFoundException;

/**
 * 추천 전용 인덱스도 임베딩 차원과 거리 척도를 기동 시 검증한다.
 *
 * <p>인덱스 자체가 없으면(NotFoundException) 경고만 남기고 넘어간다 — 추천 기능은
 * 아직 선택적이라 로컬/일부 환경에 인덱스가 없다고 앱 전체가 죽을 이유는 없다.
 * 다만 인덱스가 존재하는데 설정과 어긋나면(차원/거리척도) 여전히 기동을 실패시킨다 —
 * VectorIndexVerifier 와 같은 이유로, 조용히 틀린 결과가 나가는 걸 막는 안전장치다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.clients", havingValue = "bedrock")
public class RecommendationIndexVerifier implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(RecommendationIndexVerifier.class);

    private final S3VectorsClient s3Vectors;
    private final AiProperties props;

    public RecommendationIndexVerifier(S3VectorsClient s3Vectors, AiProperties props) {
        this.s3Vectors = s3Vectors;
        this.props = props;
    }

    @Override
    public void afterPropertiesSet() {
        AiProperties.Vector vector = props.vector();
        Index index;
        try {
            index = s3Vectors
                    .getIndex(r -> r.vectorBucketName(vector.bucketName()).indexName(vector.recommendationIndexName()))
                    .index();
        } catch (NotFoundException e) {
            log.warn(
                    "추천 벡터 인덱스 {}/{} 가 없다 — 검증을 건너뛴다. 추천 기능은 비활성 상태로 남는다.",
                    vector.bucketName(),
                    vector.recommendationIndexName());
            return;
        }
        if (index.dimension() != props.bedrock().embeddingDimension()) {
            throw new IllegalStateException("추천 벡터 인덱스 차원이 임베딩 모델과 다릅니다: " + index.dimension());
        }
        if (!vector.distanceMetric().equalsIgnoreCase(index.distanceMetricAsString())) {
            throw new IllegalStateException("추천 벡터 인덱스 거리 척도가 설정과 다릅니다: " + index.distanceMetricAsString());
        }
    }
}

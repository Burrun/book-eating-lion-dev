package com.bookeatinglion.ai.api.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.Index;

/** 추천 전용 인덱스도 임베딩 차원과 거리 척도를 기동 시 검증한다. */
@Component
@ConditionalOnProperty(name = "app.ai.clients", havingValue = "bedrock")
public class RecommendationIndexVerifier implements InitializingBean {

    private final S3VectorsClient s3Vectors;
    private final AiProperties props;

    public RecommendationIndexVerifier(S3VectorsClient s3Vectors, AiProperties props) {
        this.s3Vectors = s3Vectors;
        this.props = props;
    }

    @Override
    public void afterPropertiesSet() {
        AiProperties.Vector vector = props.vector();
        Index index = s3Vectors
                .getIndex(r -> r.vectorBucketName(vector.bucketName()).indexName(vector.recommendationIndexName()))
                .index();
        if (index.dimension() != props.bedrock().embeddingDimension()) {
            throw new IllegalStateException("추천 벡터 인덱스 차원이 임베딩 모델과 다릅니다: " + index.dimension());
        }
        if (!vector.distanceMetric().equalsIgnoreCase(index.distanceMetricAsString())) {
            throw new IllegalStateException("추천 벡터 인덱스 거리 척도가 설정과 다릅니다: " + index.distanceMetricAsString());
        }
    }
}

package com.bookeatinglion.ai.api.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.GetIndexResponse;
import software.amazon.awssdk.services.s3vectors.model.Index;
import software.amazon.awssdk.services.s3vectors.model.MetadataConfiguration;

/**
 * 차원·거리척도·비필터키는 인덱스 생성 후 못 바꾼다. 어긋난 채로 뜨면 에러 없이
 * 결과만 틀리므로, 기동을 실패시키는 것이 유일한 방어다.
 */
class VectorIndexVerifierTest {

    private static final AiProperties PROPS = new AiProperties(
            "bedrock",
            new AiProperties.Bedrock(
                    "ap-northeast-2",
                    "amazon.titan-embed-text-v2:0",
                    1024,
                    "llm",
                    1024,
                    0.2,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(10)),
            new AiProperties.Vector(
                    "bel-wiki-vectors-test", "wiki-v1", "recommendation-books-v1", "cosine", List.of("text")),
            new AiProperties.Ingest("epub-bucket"),
            new AiProperties.Sqs(false, null, 20, 1, new AiProperties.Purchase(false, null)));

    @SuppressWarnings("unchecked")
    private static VectorIndexVerifier verifierSeeing(int dimension, String metric, List<String> nonFilterable) {
        S3VectorsClient s3 = mock(S3VectorsClient.class);
        when(s3.getIndex(any(Consumer.class)))
                .thenReturn(GetIndexResponse.builder()
                        .index(Index.builder()
                                .vectorBucketName("bel-wiki-vectors-test")
                                .indexName("wiki-v1")
                                .dimension(dimension)
                                .distanceMetric(metric)
                                .metadataConfiguration(MetadataConfiguration.builder()
                                        .nonFilterableMetadataKeys(nonFilterable)
                                        .build())
                                .build())
                        .build());
        return new VectorIndexVerifier(s3, PROPS);
    }

    @Test
    void 설정과_같으면_통과한다() {
        assertThatCode(verifierSeeing(1024, "cosine", List.of("text"))::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void 차원이_다르면_기동_실패다() {
        assertThatThrownBy(verifierSeeing(512, "cosine", List.of("text"))::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("차원");
    }

    @Test
    void 거리척도가_다르면_기동_실패다() {
        assertThatThrownBy(verifierSeeing(1024, "euclidean", List.of("text"))::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("거리 척도");
    }

    /** text 가 필터 가능으로 잡히면 청크 원문이 2KB 필터 예산에 들어가 PutVectors 가 400 이다. */
    @Test
    void text_가_비필터키가_아니면_기동_실패다() {
        assertThatThrownBy(verifierSeeing(1024, "cosine", List.of())::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비필터 메타데이터 키");
    }
}

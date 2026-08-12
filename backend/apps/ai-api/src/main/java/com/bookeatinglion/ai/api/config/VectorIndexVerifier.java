package com.bookeatinglion.ai.api.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.Index;

/**
 * 기동 시 S3 Vectors 인덱스가 설정과 같은지 대조하고, 다르면 기동을 실패시킨다.
 *
 * <p>차원·거리척도·비필터키는 인덱스 생성 후 바꿀 수 없다. 어긋난 채로 뜨면 에러가 나지
 * 않고 조용히 틀린 결과만 나온다 — 그게 이 검증이 있는 이유다. 여기서 죽으면 파드가
 * Ready 가 되지 않아 잘못된 인덱스로 트래픽이 흐르지 않는다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.clients", havingValue = "bedrock")
public class VectorIndexVerifier implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexVerifier.class);

    private final S3VectorsClient s3Vectors;
    private final AiProperties props;

    public VectorIndexVerifier(S3VectorsClient s3Vectors, AiProperties props) {
        this.s3Vectors = s3Vectors;
        this.props = props;
    }

    @Override
    public void afterPropertiesSet() {
        AiProperties.Vector expected = props.vector();

        Index index = s3Vectors
                .getIndex(r -> r.vectorBucketName(expected.bucketName()).indexName(expected.indexName()))
                .index();

        int actualDimension = index.dimension();
        if (actualDimension != props.bedrock().embeddingDimension()) {
            throw new IllegalStateException(
                    fail(expected, "차원", props.bedrock().embeddingDimension(), actualDimension));
        }

        String actualMetric = index.distanceMetricAsString();
        if (!expected.distanceMetric().equalsIgnoreCase(actualMetric)) {
            throw new IllegalStateException(fail(expected, "거리 척도", expected.distanceMetric(), actualMetric));
        }

        List<String> actualNonFilterable = index.metadataConfiguration() == null
                ? List.of()
                : index.metadataConfiguration().nonFilterableMetadataKeys();
        if (!actualNonFilterable.containsAll(expected.nonFilterableMetadataKeys())) {
            throw new IllegalStateException(
                    fail(expected, "비필터 메타데이터 키", expected.nonFilterableMetadataKeys(), actualNonFilterable));
        }

        log.info(
                "S3 Vectors 인덱스 검증 통과: {}/{} dim={} metric={} nonFilterable={}",
                expected.bucketName(),
                expected.indexName(),
                actualDimension,
                actualMetric,
                actualNonFilterable);
    }

    private static String fail(AiProperties.Vector v, String what, Object expected, Object actual) {
        return "S3 Vectors 인덱스 %s/%s 의 %s 가 설정과 다르다: 기대 %s, 실제 %s. 인덱스는 생성 후 변경 불가라 재생성 + 전건 재인제스트가 필요하다."
                .formatted(v.bucketName(), v.indexName(), what, expected, actual);
    }
}

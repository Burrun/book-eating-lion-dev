package com.bookeatinglion.ai.api.client;

import com.bookeatinglion.ai.api.config.AiProperties;
import com.bookeatinglion.ai.wiki.port.VectorSearchPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.QueryOutputVector;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.VectorData;

/**
 * S3 Vectors {@code QueryVectors} 어댑터.
 *
 * <p>핵심은 {@code filter} 다. 허용된 책 목록을 {@code {"bookId": {"$in": [...]}}} 로 내려
 * **인덱스가 직접 거른다.** 전건 검색 후 자바에서 걸러내면 topK 안을 남의 책이 채워
 * 정작 볼 수 있는 책이 밀려난다 — 결과가 비는 게 아니라 조용히 나빠진다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.clients", havingValue = "bedrock")
public class S3VectorSearchAdapter implements VectorSearchPort {

    private final S3VectorsClient s3Vectors;
    private final String bucketName;
    private final String indexName;

    public S3VectorSearchAdapter(S3VectorsClient s3Vectors, AiProperties props) {
        this.s3Vectors = s3Vectors;
        this.bucketName = props.vector().bucketName();
        this.indexName = props.vector().indexName();
    }

    @Override
    public List<Match> search(float[] queryVector, Collection<Long> allowedBookIds, int topK) {
        if (allowedBookIds.isEmpty()) {
            // 빈 $in 은 "제한 없음"으로 해석될 여지가 있다. 호출 자체를 막는다.
            throw new IllegalArgumentException("허용 책 목록이 비었다. 검색을 호출하면 안 된다.");
        }

        List<Float> vector = new ArrayList<>(queryVector.length);
        for (float v : queryVector) {
            vector.add(v);
        }

        QueryVectorsResponse response = s3Vectors.queryVectors(r -> r.vectorBucketName(bucketName)
                .indexName(indexName)
                .topK(topK)
                .queryVector(VectorData.fromFloat32(vector))
                .filter(bookIdFilter(allowedBookIds))
                .returnMetadata(true)
                .returnDistance(true));

        return response.vectors().stream()
                .map(S3VectorSearchAdapter::toMatch)
                .sorted(Comparator.comparingDouble(Match::distance))
                .toList();
    }

    private static Document bookIdFilter(Collection<Long> allowedBookIds) {
        List<Document> ids = allowedBookIds.stream()
                .map(id -> Document.fromNumber(id.longValue()))
                .toList();
        return Document.fromMap(Map.of("bookId", Document.fromMap(Map.of("$in", Document.fromList(ids)))));
    }

    /**
     * 메타데이터가 기대와 다르면 예외를 던진다. 건너뛰면 인덱스가 망가진 채로도 응답이
     * 그럴싸해서 아무도 모른다 — 호출자는 이 예외를 grounded=false 로 낮추므로
     * 사용자에게는 "모르겠다"가, 로그에는 원인이 남는다.
     */
    private static Match toMatch(QueryOutputVector vector) {
        Map<String, Document> meta =
                vector.metadata() == null ? Map.of() : vector.metadata().asMap();

        return new Match(
                required(meta, "bookId", vector.key()).asNumber().longValue(),
                required(meta, "bookTitle", vector.key()).asString(),
                required(meta, "page", vector.key()).asNumber().intValue(),
                required(meta, "text", vector.key()).asString(),
                vector.distance());
    }

    private static Document required(Map<String, Document> meta, String key, String vectorKey) {
        Document value = meta.get(key);
        if (value == null || value.isNull()) {
            throw new IllegalStateException("벡터 %s 의 메타데이터에 %s 가 없다. 인제스트가 계약과 다르게 적재했다.".formatted(vectorKey, key));
        }
        return value;
    }
}

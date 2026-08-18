package com.bookeatinglion.ai.api.client;

import com.bookeatinglion.ai.api.config.AiProperties;
import com.bookeatinglion.ai.recommendation.port.RecommendationVectorPort;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.PutInputVector;
import software.amazon.awssdk.services.s3vectors.model.VectorData;

/** 추천 도서 한 권을 하나의 벡터로 저장하는 전용 인덱스 어댑터. RAG 청크 인덱스와 섞지 않는다. */
@Component
@ConditionalOnProperty(name = "app.ai.clients", havingValue = "bedrock", matchIfMissing = true)
public class S3RecommendationVectorAdapter implements RecommendationVectorPort {

    private final S3VectorsClient s3Vectors;
    private final String bucketName;
    private final String indexName;

    public S3RecommendationVectorAdapter(S3VectorsClient s3Vectors, AiProperties props) {
        this.s3Vectors = s3Vectors;
        this.bucketName = props.vector().bucketName();
        this.indexName = props.vector().recommendationIndexName();
    }

    @Override
    public void upsert(BookVector book) {
        s3Vectors.putVectors(
                r -> r.vectorBucketName(bucketName).indexName(indexName).vectors(toInput(book)));
    }

    @Override
    public void delete(long bookId) {
        s3Vectors.deleteVectors(
                r -> r.vectorBucketName(bucketName).indexName(indexName).keys(key(bookId)));
    }

    @Override
    public List<Match> search(float[] queryVector, int topK) {
        return s3Vectors
                .queryVectors(r -> r.vectorBucketName(bucketName)
                        .indexName(indexName)
                        .topK(topK)
                        .queryVector(VectorData.fromFloat32(floats(queryVector)))
                        .returnMetadata(true)
                        .returnDistance(true))
                .vectors()
                .stream()
                .map(vector -> {
                    Map<String, Document> metadata = vector.metadata().asMap();
                    return new Match(
                            metadata.get("bookId").asNumber().longValue(),
                            metadata.get("title").asString(),
                            metadata.get("author").asString(),
                            metadata.get("category").asString(),
                            vector.distance());
                })
                .sorted(Comparator.comparingDouble(Match::distance))
                .toList();
    }

    private static PutInputVector toInput(BookVector book) {
        return PutInputVector.builder()
                .key(key(book.bookId()))
                .data(VectorData.fromFloat32(floats(book.embedding())))
                .metadata(Document.fromMap(Map.of(
                        "bookId", Document.fromNumber(book.bookId()),
                        "title", Document.fromString(book.title()),
                        "author", Document.fromString(book.author()),
                        "category", Document.fromString(book.category()))))
                .build();
    }

    private static String key(long bookId) {
        return "recommendation-book#" + bookId;
    }

    private static List<Float> floats(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }
}

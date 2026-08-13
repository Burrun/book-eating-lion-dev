package com.bookeatinglion.ai.api.client;

import com.bookeatinglion.ai.api.config.AiProperties;
import com.bookeatinglion.ai.wiki.port.VectorIndexPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.GetVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.PutInputVector;
import software.amazon.awssdk.services.s3vectors.model.VectorData;

/**
 * S3 Vectors 쓰기 어댑터.
 *
 * <p>🔴 <b>{@code ListVectors} 를 쓰지 않는다.</b> 거기엔 prefix 파라미터가 없어서 "이 책의
 * 벡터"를 찾으려면 인덱스를 전수 스캔해야 하고, 그러면 책 1권 인제스트가 인덱스 크기에
 * 선형으로 느려진다. 삭제 대상 키는 {@code wiki_book_chunks} 가 들고 있고, 검증은
 * {@code GetVectors} 로 우리가 넣은 키만 조회한다 — 둘 다 비용이 청크 수에만 비례한다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.clients", havingValue = "bedrock", matchIfMissing = true)
public class S3VectorIndexAdapter implements VectorIndexPort {

    /** PutVectors/DeleteVectors/GetVectors 한 번에 보낼 건수. 요청 크기 상한에 여유를 두고 잡았다. */
    private static final int BATCH = 100;

    private final S3VectorsClient s3Vectors;
    private final String bucketName;
    private final String indexName;

    public S3VectorIndexAdapter(S3VectorsClient s3Vectors, AiProperties props) {
        this.s3Vectors = s3Vectors;
        this.bucketName = props.vector().bucketName();
        this.indexName = props.vector().indexName();
    }

    @Override
    public void delete(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        for (List<String> batch : partition(keys, BATCH)) {
            s3Vectors.deleteVectors(
                    r -> r.vectorBucketName(bucketName).indexName(indexName).keys(batch));
        }
    }

    @Override
    public void put(List<VectorRecord> vectors) {
        List<PutInputVector> inputs =
                vectors.stream().map(S3VectorIndexAdapter::toInput).toList();
        for (List<PutInputVector> batch : partition(inputs, BATCH)) {
            s3Vectors.putVectors(
                    r -> r.vectorBucketName(bucketName).indexName(indexName).vectors(batch));
        }
    }

    /**
     * 우리가 넣은 키만 조회한다. 인덱스 전수 스캔({@code ListVectors})이 아니라 {@code GetVectors}
     * 라서 인덱스가 커져도 비용이 청크 수에 비례한다.
     */
    @Override
    public long countExisting(List<String> keys) {
        long found = 0;
        for (List<String> batch : partition(keys, BATCH)) {
            GetVectorsResponse response = s3Vectors.getVectors(
                    r -> r.vectorBucketName(bucketName).indexName(indexName).keys(batch));
            found += response.vectors().size();
        }
        return found;
    }

    /**
     * 키는 호출자가 주지만 그대로 믿지 않는다. 접두사 삭제가 키 규칙에 의존하므로, 규칙이
     * 깨진 키가 하나라도 섞이면 그 책의 옛 벡터가 영원히 안 지워진다 — 조용히 고아가 된다.
     */
    private static PutInputVector toInput(VectorRecord record) {
        String expected = VectorIndexPort.key(record.bookId(), record.page(), seqOf(record.key()));
        if (!expected.equals(record.key())) {
            throw new IllegalStateException("키 규칙 위반: 기대 %s, 실제 %s".formatted(expected, record.key()));
        }

        List<Float> data = new ArrayList<>(record.embedding().length);
        for (float f : record.embedding()) {
            data.add(f);
        }

        return PutInputVector.builder()
                .key(record.key())
                .data(VectorData.fromFloat32(data))
                .metadata(Document.fromMap(Map.of(
                        "bookId", Document.fromNumber(record.bookId()),
                        "bookTitle", Document.fromString(record.bookTitle()),
                        "category", Document.fromString(record.category()),
                        "page", Document.fromNumber(record.page()),
                        "text", Document.fromString(record.text()))))
                .build();
    }

    private static int seqOf(String key) {
        int last = key.lastIndexOf('#');
        try {
            return Integer.parseInt(key.substring(last + 1));
        } catch (RuntimeException e) {
            throw new IllegalStateException("키에서 chunkSeq 를 읽지 못했다: " + key, e);
        }
    }

    private static <T> List<List<T>> partition(List<T> items, int size) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            batches.add(items.subList(i, Math.min(i + size, items.size())));
        }
        return batches;
    }
}

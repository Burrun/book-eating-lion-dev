package com.bookeatinglion.ai.client;

/**
 * 질의/청크 임베딩 생성. Phase 0-2b 에서 Bedrock Titan Text Embeddings V2(1024차원)로 확정했다.
 *
 * 이 결정이 HPA 메트릭까지 정한다 — 외부 API 이므로 임베딩 생성은 I/O 바운드다.
 * 따라서 ai-rag 의 HPA 는 CPU 가 아니라 동시 요청 수 기준이다(판단 ④의 표).
 * 로컬 임베딩 모델을 골랐다면 CPU 70% 가 맞았겠지만, 그쪽을 택하지 않았다.
 */
public interface EmbeddingClient {

    /** S3 Vectors 인덱스 `wiki-v1` 의 차원. 인덱스는 생성 후 차원을 못 바꾼다. */
    int DIMENSION = 1024;

    String MODEL_ID = "amazon.titan-embed-text-v2:0";

    /**
     * @return 길이가 정확히 {@link #DIMENSION} 인 벡터. 다르면 구현이 예외를 던진다 —
     *     차원이 어긋난 벡터를 그대로 흘리면 PutVectors/QueryVectors 가 400 을 내거나,
     *     더 나쁘게는 조용히 엉뚱한 결과를 준다.
     */
    float[] embed(String text);
}

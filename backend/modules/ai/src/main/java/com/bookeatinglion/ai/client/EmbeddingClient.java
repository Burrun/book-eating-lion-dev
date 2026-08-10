package com.bookeatinglion.ai.client;

/**
 * 질의/기록 임베딩 생성. Phase 0-2b 에서 Bedrock Titan Text Embeddings V2(1024차원)로 확정했다.
 *
 * 이 결정이 HPA 메트릭까지 정한다 — 외부 API 이므로 임베딩 생성은 I/O 바운드다.
 * 따라서 ai-rag 의 HPA 는 CPU 가 아니라 동시 요청 수 기준이다(판단 ④의 표).
 * 로컬 임베딩 모델을 골랐다면 CPU 70% 가 맞았겠지만, 그쪽을 택하지 않았다.
 */
public interface EmbeddingClient {

    int DIMENSION = 1024;
    String MODEL_ID = "amazon.titan-embed-text-v2:0";

    /**
     * @return pgvector 리터럴 형식의 임베딩 (예: "[0.1,0.2,...]")
     */
    String embed(String text);
}

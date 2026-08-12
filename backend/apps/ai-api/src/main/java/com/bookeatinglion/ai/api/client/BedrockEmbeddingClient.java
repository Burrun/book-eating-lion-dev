package com.bookeatinglion.ai.api.client;

import com.bookeatinglion.ai.api.config.AiProperties;
import com.bookeatinglion.ai.client.EmbeddingClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * Titan Text Embeddings V2. Converse API 는 임베딩 모델을 지원하지 않으므로
 * {@code InvokeModel} + 모델 고유 페이로드를 쓴다.
 *
 * <p>{@code normalize: true} 로 요청한다 — 인덱스 거리 척도가 cosine 이라 정규화된
 * 벡터를 넣어야 거리 값의 의미가 인제스트/질의 사이에서 같다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.clients", havingValue = "bedrock")
public class BedrockEmbeddingClient implements EmbeddingClient {

    private final BedrockRuntimeClient bedrock;
    private final ObjectMapper mapper;
    private final String modelId;
    private final int dimension;

    public BedrockEmbeddingClient(BedrockRuntimeClient bedrock, ObjectMapper mapper, AiProperties props) {
        this.bedrock = bedrock;
        this.mapper = mapper;
        this.modelId = props.bedrock().embeddingModel();
        this.dimension = props.bedrock().embeddingDimension();
    }

    @Override
    public float[] embed(String text) {
        String payload;
        try {
            payload = mapper.writeValueAsString(Map.of("inputText", text, "dimensions", dimension, "normalize", true));
        } catch (Exception e) {
            throw new IllegalStateException("임베딩 요청 직렬화 실패", e);
        }

        InvokeModelResponse response = bedrock.invokeModel(r -> r.modelId(modelId)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(payload)));

        JsonNode embedding;
        try {
            embedding = mapper.readTree(response.body().asUtf8String()).path("embedding");
        } catch (Exception e) {
            throw new IllegalStateException("임베딩 응답 파싱 실패", e);
        }

        // 차원이 어긋난 벡터를 그대로 흘리면 PutVectors 가 400 을 내거나, 더 나쁘게는
        // 인덱스가 조용히 엉뚱한 결과를 준다. 여기서 끊는다.
        if (!embedding.isArray() || embedding.size() != dimension) {
            throw new IllegalStateException(
                    "임베딩 차원 불일치: 기대 " + dimension + ", 실제 " + (embedding.isArray() ? embedding.size() : -1));
        }

        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = (float) embedding.get(i).asDouble();
        }
        return vector;
    }
}

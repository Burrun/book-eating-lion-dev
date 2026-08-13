package com.bookeatinglion.ai.api.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.ai.api.config.AiProperties;
import com.bookeatinglion.ai.client.EmbeddingClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * 이 테스트가 지키는 것은 "Bedrock 이 응답했다"가 아니라 "차원이 어긋난 벡터가
 * 파이프라인으로 흘러 나가지 않는다"이다. 어긋난 벡터는 예외를 내지 않고 조용히
 * 틀린 검색 결과를 만든다.
 */
class BedrockEmbeddingClientTest {

    private static final AiProperties PROPS = new AiProperties(
            "bedrock",
            new AiProperties.Bedrock(
                    "ap-northeast-2",
                    EmbeddingClient.MODEL_ID,
                    EmbeddingClient.DIMENSION,
                    "anthropic.claude-3-5-sonnet-20241022-v2:0",
                    1024,
                    0.2,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(10)),
            new AiProperties.Vector("bucket", "wiki-v1", "cosine", List.of("text")),
            new AiProperties.Ingest("epub-bucket"),
            new AiProperties.Sqs(false, null, 20, 1, new AiProperties.Purchase(false, null)));

    private final BedrockRuntimeClient bedrock = mock(BedrockRuntimeClient.class);
    private final BedrockEmbeddingClient client = new BedrockEmbeddingClient(bedrock, new ObjectMapper(), PROPS);

    @SuppressWarnings("unchecked")
    private void givenEmbeddingOfLength(int length) {
        String body = IntStream.range(0, length)
                .mapToObj(i -> "0.01")
                .collect(Collectors.joining(",", "{\"embedding\":[", "]}"));
        when(bedrock.invokeModel(any(Consumer.class)))
                .thenReturn(InvokeModelResponse.builder()
                        .body(SdkBytes.fromUtf8String(body))
                        .build());
    }

    @Test
    void 임베딩은_1024차원이다() {
        givenEmbeddingOfLength(EmbeddingClient.DIMENSION);

        assertThat(client.embed("김첨지는 설렁탕을 샀다")).hasSize(EmbeddingClient.DIMENSION);
    }

    @Test
    void 차원이_다르면_예외다() {
        givenEmbeddingOfLength(512);

        assertThatThrownBy(() -> client.embed("아무 문장"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("차원 불일치");
    }

    /** normalize 를 빠뜨리면 cosine 인덱스에서 거리 값의 의미가 인제스트/질의 사이에서 달라진다. */
    @Test
    @SuppressWarnings("unchecked")
    void 요청에_차원과_normalize_가_들어간다() throws Exception {
        givenEmbeddingOfLength(EmbeddingClient.DIMENSION);
        client.embed("질의");

        ArgumentCaptor<Consumer<InvokeModelRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(bedrock).invokeModel(captor.capture());
        InvokeModelRequest.Builder builder = InvokeModelRequest.builder();
        captor.getValue().accept(builder);

        var payload = new ObjectMapper().readTree(builder.build().body().asUtf8String());
        assertThat(payload.path("dimensions").asInt()).isEqualTo(EmbeddingClient.DIMENSION);
        assertThat(payload.path("normalize").asBoolean()).isTrue();
        assertThat(payload.path("inputText").asText()).isEqualTo("질의");
    }
}

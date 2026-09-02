package com.bookeatinglion.ai.api.client;

import com.bookeatinglion.ai.api.config.AiProperties;
import com.bookeatinglion.ai.client.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

/**
 * Converse API 로 응답을 생성한다. {@code InvokeModel} 과 달리 모델 고유 페이로드를
 * 만들 필요가 없어서, 모델을 바꿔도 설정 한 줄이면 된다.
 *
 * <p>
 * temperature 를 낮게 잡는다 — 이 서비스의 답은 창작이 아니라 인용이다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.clients", havingValue = "bedrock", matchIfMissing = true)
public class BedrockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(BedrockLlmClient.class);

    private final BedrockRuntimeClient bedrock;
    private final String modelId;
    private final int maxTokens;
    private final float temperature;

    public BedrockLlmClient(BedrockRuntimeClient bedrock, AiProperties props) {
        this.bedrock = bedrock;
        this.modelId = props.bedrock().llmModel();
        this.maxTokens = props.bedrock().llmMaxTokens();
        this.temperature = (float) props.bedrock().llmTemperature();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Message message = Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(userPrompt))
                .build();

        ConverseResponse response = bedrock.converse(r -> r.modelId(modelId)
                .system(SystemContentBlock.fromText(systemPrompt))
                .messages(message)
                .inferenceConfig(c -> c.maxTokens(maxTokens).temperature(temperature)));

        logUsage(response);

        return response.output().message().content().get(0).text();
    }

    /**
     * 호출당 실제 토큰 수와 지연을 남긴다. 비용을 추정으로 이야기하지 않으려면 이게 유일한 근거다 —
     * 한국어는 토크나이저마다 글자당 토큰 수가 크게 달라서 글자 수로 환산하면 몇 배씩 틀린다.
     *
     * <p>
     * {@code MAX_TOKENS} 로 끝난 응답은 문장 중간에서 잘린 답이다. 인용 서비스에서는 이게
     * "[1]" 같은 인용 표기가 잘려나간 답으로 나가므로, 조용히 넘기지 않고 WARN 을 남긴다.
     */
    private void logUsage(ConverseResponse response) {
        TokenUsage usage = response.usage();
        Long latencyMs = response.metrics() == null ? null : response.metrics().latencyMs();

        log.info(
                "bedrock.converse model={} inputTokens={} outputTokens={} totalTokens={} latencyMs={} stopReason={}",
                modelId,
                usage == null ? null : usage.inputTokens(),
                usage == null ? null : usage.outputTokens(),
                usage == null ? null : usage.totalTokens(),
                latencyMs,
                response.stopReasonAsString());

        if (response.stopReason() == StopReason.MAX_TOKENS) {
            log.warn("LLM 응답이 max-tokens 에서 잘렸다. 인용 번호가 잘려나갔을 수 있으니 app.ai.bedrock.llm-max-tokens 를 확인할 것.");
        }
    }
}

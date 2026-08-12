package com.bookeatinglion.ai.api.client;

import com.bookeatinglion.ai.client.LlmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** AWS 자격증명 없이 로컬을 띄우기 위한 스텁. 판별 방식은 {@link StubEmbeddingClient} 와 같다. */
@Component
@ConditionalOnProperty(name = "app.ai.clients", havingValue = "stub", matchIfMissing = true)
public class StubLlmClient implements LlmClient {

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return "[stub] " + userPrompt;
    }
}

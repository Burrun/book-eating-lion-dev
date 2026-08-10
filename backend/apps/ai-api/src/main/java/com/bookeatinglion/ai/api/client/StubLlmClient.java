package com.bookeatinglion.ai.api.client;

import com.bookeatinglion.ai.client.LlmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Bedrock 연동 전 자리를 채우는 스텁. Phase 1 의 ai 팀 산출물로 교체된다. */
@Component
@ConditionalOnMissingBean(name = "bedrockLlmClient")
public class StubLlmClient implements LlmClient {

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return "[stub] " + userPrompt;
    }
}

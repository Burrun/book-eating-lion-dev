package com.bookeatinglion.ai.wiki.service;

import com.bookeatinglion.ai.client.EmbeddingClient;
import com.bookeatinglion.ai.client.LlmClient;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 외부 API 호출을 Bulkhead 로 감싸기 위해서만 존재하는 얇은 층이다.
 *
 * <p>🔴 <b>인라인하지 말 것.</b> {@code @Bulkhead} 는 Spring AOP 프록시를 통해야 걸린다.
 * 이 메서드들을 {@link WikiRagService} 안으로 옮기면 자기 자신 호출(self-invocation)이
 * 되어 프록시를 거치지 않고, 애너테이션은 그대로인데 격리만 조용히 사라진다.
 *
 * <p>클라이언트 구현({@code BedrockLlmClient})에 직접 걸지 않는 이유는 문의봇이
 * 같은 빈을 쓰기 때문이다. 거기 걸면 {@code aiExternalApi} 위에 {@code aiLlm} 이
 * 한 겹 더 얹혀 봇의 동시 호출 상한이 말없이 줄어든다 — 봇은 건드리지 않기로 한 범위다.
 */
@Component
@RequiredArgsConstructor
public class GuardedAiCalls {

    private final EmbeddingClient embeddingClient;
    private final LlmClient llmClient;

    @Bulkhead(name = "aiEmbedding")
    public float[] embed(String text) {
        return embeddingClient.embed(text);
    }

    @Bulkhead(name = "aiLlm")
    public String complete(String systemPrompt, String userPrompt) {
        return llmClient.complete(systemPrompt, userPrompt);
    }
}

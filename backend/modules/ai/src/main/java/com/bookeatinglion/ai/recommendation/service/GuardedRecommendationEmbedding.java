package com.bookeatinglion.ai.recommendation.service;

import com.bookeatinglion.ai.wiki.service.GuardedAiCalls;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GuardedRecommendationEmbedding {

    private final GuardedAiCalls ai;
    private final RecommendationEmbeddingCache cache;

    public float[] create(long bookId, String text) {
        return cache.getOrCreate(bookId, text, () -> ai.embed(text));
    }
}

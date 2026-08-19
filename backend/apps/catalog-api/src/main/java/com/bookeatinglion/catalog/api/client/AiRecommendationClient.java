package com.bookeatinglion.catalog.api.client;

import com.bookeatinglion.book.port.RecommendationAiPort.RankedBook;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ai-recommendation", url = "${services.ai.url}", fallback = AiRecommendationFallback.class)
public interface AiRecommendationClient {

    @PostMapping("/internal/ai/recommendations/rank")
    List<RankedBook> rank(@RequestBody RankRequest request);

    record RankRequest(String memberId, String preferenceEvidence, int topK) {}
}

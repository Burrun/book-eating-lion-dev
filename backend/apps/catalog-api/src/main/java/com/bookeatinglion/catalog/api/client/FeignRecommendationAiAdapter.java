package com.bookeatinglion.catalog.api.client;

import com.bookeatinglion.book.port.RecommendationAiPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeignRecommendationAiAdapter implements RecommendationAiPort {

    private final AiRecommendationClient client;

    @Override
    public List<RankedBook> rank(String memberId, String preferenceEvidence, int topK) {
        return client.rank(new AiRecommendationClient.RankRequest(memberId, preferenceEvidence, topK));
    }
}

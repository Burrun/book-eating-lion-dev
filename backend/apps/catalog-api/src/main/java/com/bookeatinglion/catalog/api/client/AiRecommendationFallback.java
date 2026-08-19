package com.bookeatinglion.catalog.api.client;

import com.bookeatinglion.book.port.RecommendationAiPort.RankedBook;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiRecommendationFallback implements AiRecommendationClient {

    @Override
    public List<RankedBook> rank(RankRequest request) {
        log.warn("AI 추천 서비스 호출 실패 — 규칙 기반 추천으로 전환합니다. memberId={}", request.memberId());
        return List.of();
    }
}

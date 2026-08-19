package com.bookeatinglion.ai.recommendation.service;

import com.bookeatinglion.ai.recommendation.port.RecommendationVectorPort;
import com.bookeatinglion.ai.recommendation.port.RecommendationVectorPort.BookVector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationIndexingService {

    private final GuardedRecommendationEmbedding embedding;
    private final RecommendationVectorPort vectorPort;

    public void upsert(long bookId, String title, String author, String category, String description) {
        String text = "제목: %s\n작가: %s\n카테고리: %s\n설명: %s"
                .formatted(title, author, category, description == null ? "" : description);
        vectorPort.upsert(new BookVector(bookId, title, author, category, description, embedding.create(bookId, text)));
    }

    public void delete(long bookId) {
        vectorPort.delete(bookId);
    }
}

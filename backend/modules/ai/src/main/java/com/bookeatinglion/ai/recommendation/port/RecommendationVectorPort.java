package com.bookeatinglion.ai.recommendation.port;

import java.util.List;

public interface RecommendationVectorPort {

    void upsert(BookVector book);

    void delete(long bookId);

    List<Match> search(float[] queryVector, int topK);

    record BookVector(
            long bookId, String title, String author, String category, String description, float[] embedding) {}

    record Match(long bookId, String title, String author, String category, double distance) {}
}

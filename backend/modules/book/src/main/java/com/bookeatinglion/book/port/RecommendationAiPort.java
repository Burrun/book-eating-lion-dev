package com.bookeatinglion.book.port;

import java.util.List;

public interface RecommendationAiPort {

    List<RankedBook> rank(String memberId, String preferenceEvidence, int topK);

    record RankedBook(Long bookId, double semanticScore, String reason) {}
}

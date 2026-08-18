package com.bookeatinglion.book.dto;

import java.util.List;
import java.util.UUID;

public record RecommendationQueueResponse(UUID queueId, List<RecommendationCardResponse> cards) {}

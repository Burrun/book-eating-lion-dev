package com.bookeatinglion.book.port;

import com.bookeatinglion.book.dto.RecommendationQueueResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationQueuePort {

    Optional<RecommendationQueueResponse> get(String memberId);

    void put(String memberId, RecommendationQueueResponse queue, Duration ttl);

    void removeCard(String memberId, UUID queueId, Long bookId, Duration ttl);
}

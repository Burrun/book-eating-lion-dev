package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.RecommendationExposure;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationExposureRepository extends JpaRepository<RecommendationExposure, Long> {

    Optional<RecommendationExposure> findByQueueIdAndMemberIdAndBook_BookId(UUID queueId, String memberId, Long bookId);
}

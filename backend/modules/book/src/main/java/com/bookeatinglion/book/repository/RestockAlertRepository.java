package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.RestockAlert;
import com.bookeatinglion.book.domain.RestockAlertStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestockAlertRepository extends JpaRepository<RestockAlert, Long> {
    Optional<RestockAlert> findByMemberIdAndBookBookId(String memberId, Long bookId);

    List<RestockAlert> findByMemberIdOrderByRequestedAtDesc(String memberId);

    List<RestockAlert> findByMemberIdAndStatusOrderByRequestedAtDesc(String memberId, RestockAlertStatus status);

    List<RestockAlert> findByBookBookIdAndStatus(Long bookId, RestockAlertStatus status);

    List<RestockAlert> findTop100ByStatusAndNextRetryAtLessThanEqualAndRetryCountLessThanOrderByNextRetryAtAsc(
            RestockAlertStatus status, LocalDateTime now, int maxRetryCount);
}

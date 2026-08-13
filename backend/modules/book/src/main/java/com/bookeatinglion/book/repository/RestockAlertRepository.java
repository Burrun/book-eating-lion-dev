package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.RestockAlert;
import com.bookeatinglion.book.domain.RestockAlertStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestockAlertRepository extends JpaRepository<RestockAlert, Long> {
    Optional<RestockAlert> findByMemberIdAndBookBookId(String memberId, Long bookId);

    List<RestockAlert> findByMemberIdOrderByRequestedAtDesc(String memberId);

    List<RestockAlert> findByMemberIdAndStatusOrderByRequestedAtDesc(String memberId, RestockAlertStatus status);

    List<RestockAlert> findByBookBookIdAndStatus(Long bookId, RestockAlertStatus status);

    List<RestockAlert> findTop100ByStatusAndNextRetryAtLessThanEqualAndRetryCountLessThanOrderByNextRetryAtAsc(
            RestockAlertStatus status, LocalDateTime now, int maxRetryCount);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select alert from RestockAlert alert join fetch alert.book where alert.restockAlertId = :alertId")
    Optional<RestockAlert> findByIdForUpdate(@Param("alertId") Long alertId);
}

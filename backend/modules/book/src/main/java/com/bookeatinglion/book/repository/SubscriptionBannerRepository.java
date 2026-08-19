package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.SubscriptionBanner;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionBannerRepository extends JpaRepository<SubscriptionBanner, Long> {

    List<SubscriptionBanner> findAllByOrderBySortOrderAscBannerIdAsc();

    @Query("select b from SubscriptionBanner b "
            + "where b.active = true and b.startAt <= :now and b.endAt >= :now "
            + "order by b.sortOrder asc, b.bannerId asc")
    List<SubscriptionBanner> findCurrentlyActive(@Param("now") LocalDateTime now);
}

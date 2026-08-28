package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.ReadingProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, Long> {

    Optional<ReadingProgress> findByMemberSubAndBook_BookId(String memberSub, Long bookId);

    /**
     * 완독했지만 아직 사자에게 안 먹인 책. 카드에 제목을 그리므로 미리 조인해 온다.
     * 완독 기준(percentage)은 호출자가 설정값으로 넘긴다 — 여기 상수로 굳히지 않는다.
     */
    @Query(
            """
            select p from ReadingProgress p join fetch p.book b
            where p.memberSub = :memberSub
              and p.fedAt is null
              and p.percentage >= :completionPercentage
              and b.isDeleted = false
            order by p.updatedAt desc
            """)
    List<ReadingProgress> findFeedable(String memberSub, int completionPercentage);
}

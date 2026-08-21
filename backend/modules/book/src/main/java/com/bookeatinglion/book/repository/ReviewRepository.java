package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByBook_BookId(Long bookId, Pageable pageable);

    java.util.List<Review> findByMemberId(String memberId);

    java.util.List<Review> findByMemberIdOrderByCreatedAtDesc(String memberId);

    @Query(
            """
            select coalesce(avg(r.rating), 0.0) as averageRating,
                   count(r) as reviewCount
            from Review r
            where r.book.bookId = :bookId
            """)
    ReviewStatistics findStatisticsByBookId(@Param("bookId") Long bookId);
}

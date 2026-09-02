package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.RecentViewedBook;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecentViewedBookRepository extends JpaRepository<RecentViewedBook, Long> {

    Optional<RecentViewedBook> findByMemberIdAndBook_BookId(String memberId, Long bookId);

    List<RecentViewedBook> findByMemberIdOrderByViewedAtDesc(String memberId, Pageable pageable);

    List<RecentViewedBook> findByMemberId(String memberId);
}

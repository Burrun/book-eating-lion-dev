package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.SearchHistory;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    List<SearchHistory> findByMemberIdOrderByCreatedAtDesc(String memberId, Pageable pageable);
}

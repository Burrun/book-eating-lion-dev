package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.BookHighlight;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookHighlightRepository extends JpaRepository<BookHighlight, Long> {

    /** 마이페이지 "내 메모" 목록. 책 제목을 같이 그리므로 N+1을 피해 미리 조인해 온다. */
    @Query("select h from BookHighlight h join fetch h.book where h.memberSub = :memberSub order by h.createdAt desc")
    List<BookHighlight> findAllByMemberSub(String memberSub);

    Optional<BookHighlight> findByBookHighlightIdAndMemberSub(Long bookHighlightId, String memberSub);
}

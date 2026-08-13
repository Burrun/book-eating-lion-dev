package com.bookeatinglion.ai.wiki.repository;

import com.bookeatinglion.ai.wiki.domain.FedBook;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FedBookRepository extends JpaRepository<FedBook, FedBook.Key> {

    /**
     * Redis 캐시 미스일 때만 부른다. 질의 경로가 매번 여기를 타면 커넥션이 열려 있어
     * {@code minimum-idle: 0} 이 무의미해진다 — 그래서 원본이지 읽기 경로가 아니다.
     */
    @Query("select f.bookId from FedBook f where f.memberId = :memberId order by f.bookId")
    List<Long> findBookIdsByMemberId(@Param("memberId") String memberId);

    long countByMemberId(String memberId);
}

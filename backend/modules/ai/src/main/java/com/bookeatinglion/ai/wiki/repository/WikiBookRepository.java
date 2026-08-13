package com.bookeatinglion.ai.wiki.repository;

import com.bookeatinglion.ai.wiki.domain.WikiBook;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WikiBookRepository extends JpaRepository<WikiBook, Long> {

    /**
     * 인제스트됐고 아직 안 먹은 책. catalog-service 를 호출하지 않는다.
     *
     * <p>먹은 게 하나도 없으면 서브쿼리가 빈 집합이고, SQL 의 {@code NOT IN ()} 은
     * 그때 전건을 통과시킨다 — 처음 온 사용자에게 전체 목록이 나오는 게 맞다.
     */
    @Query(
            """
            select w from WikiBook w
            where w.bookId not in (select f.bookId from FedBook f where f.memberId = :memberId)
            order by w.bookId
            """)
    List<WikiBook> findFeedableFor(@Param("memberId") String memberId);
}

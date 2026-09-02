package com.bookeatinglion.ai.wiki.repository;

import com.bookeatinglion.ai.wiki.domain.WikiBook;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 관리자 배치가 인제스트한 책 본문 목록. "먹일 수 있는 책" 판단은 더 이상 이 테이블을
 * 쓰지 않는다(catalog-service의 완독+메모 작성 여부로 대체) — 이 리포지토리는 책 본문
 * 인제스트(BookIngestService) 자체를 위해 남아 있다.
 */
public interface WikiBookRepository extends JpaRepository<WikiBook, Long> {

    /**
     * 인제스트가 끝난 책 전체. 구독 회원의 검색 허용 목록이다 —
     * {@code VectorSearchPort} 가 빈 목록을 "제한 없음"으로 해석하므로, "전체 허용"도
     * 반드시 명시적인 id 목록으로 넘겨야 한다.
     */
    @Query("select b.bookId from WikiBook b")
    List<Long> findAllBookIds();
}

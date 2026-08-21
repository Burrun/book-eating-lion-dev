package com.bookeatinglion.ai.wiki.repository;

import com.bookeatinglion.ai.wiki.domain.WikiBook;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 관리자 배치가 인제스트한 책 본문 목록. "먹일 수 있는 책" 판단은 더 이상 이 테이블을
 * 쓰지 않는다(catalog-service의 완독+메모 작성 여부로 대체) — 이 리포지토리는 책 본문
 * 인제스트(BookIngestService) 자체를 위해 남아 있다.
 */
public interface WikiBookRepository extends JpaRepository<WikiBook, Long> {}

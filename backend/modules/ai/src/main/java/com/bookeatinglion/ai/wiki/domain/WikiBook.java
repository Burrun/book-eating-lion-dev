package com.bookeatinglion.ai.wiki.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인제스트가 끝난 책. "먹일 수 있는 책"의 정의가 곧 이 테이블이다.
 *
 * <p>이게 있어서 feedable-books 가 catalog-service 를 호출하지 않는다 — "먹일 수 있다"는
 * 곧 "우리 인덱스에 있다"이고 그건 ai_db 가 안다.
 *
 * <p>쓰기는 관리자 인제스트 Job 만 한다. 서빙 경로는 읽기 전용이라 updated_at 은 매핑하지 않는다.
 */
@Entity
@Table(name = "wiki_books")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WikiBook {

    /** FK 없음: catalog_db 경계 밖. 값은 인제스트 Job 이 정하므로 생성 전략이 없다. */
    @Id
    @Column(name = "book_id")
    private Long bookId;

    /** 인제스트 시점 스냅샷. */
    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int pages;

    /** 인제스트 Job 의 건수 검증 기준값. */
    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "ingested_at", nullable = false)
    private LocalDateTime ingestedAt;

    public WikiBook(Long bookId, String title, int pages, int chunkCount, LocalDateTime ingestedAt) {
        this.bookId = bookId;
        this.title = title;
        this.pages = pages;
        this.chunkCount = chunkCount;
        this.ingestedAt = ingestedAt;
    }
}

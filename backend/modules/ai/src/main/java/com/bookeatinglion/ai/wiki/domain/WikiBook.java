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
 * <p>"먹일 수 있는 메모" 판단(완독 + 메모 작성)은 이제 catalog-service(book_memos)가 한다 —
 * 이 테이블은 책 본문이 검색 가능한 상태인지만 나타낸다.
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

    /**
     * 원본 파일의 SHA-256. 같은 파일이 다시 오면 임베딩을 건너뛰는 근거다.
     *
     * <p>nullable 인 이유는 JSONL 코퍼스 배치가 원본 파일 단위가 아니라 이 값을 못 채우기
     * 때문이다. null 이면 항상 재인제스트한다 — 건너뛰는 쪽이 기본값이면 안 된다.
     */
    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "ingested_at", nullable = false)
    private LocalDateTime ingestedAt;

    public WikiBook(Long bookId, String title, int pages, int chunkCount, LocalDateTime ingestedAt) {
        this(bookId, title, pages, chunkCount, null, ingestedAt);
    }

    public WikiBook(Long bookId, String title, int pages, int chunkCount, String sourceHash, LocalDateTime ingestedAt) {
        this.bookId = bookId;
        this.title = title;
        this.pages = pages;
        this.chunkCount = chunkCount;
        this.sourceHash = sourceHash;
        this.ingestedAt = ingestedAt;
    }
}

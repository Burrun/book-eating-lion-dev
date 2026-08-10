package com.bookeatinglion.ai.lion.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 독서 기록. RAG 의 검색 대상이다.
 *
 * 원본 스키마는 embedding 이 JSON 컬럼이라 유사도 검색이 인덱스를 못 탔고,
 * 전건 로드 후 애플리케이션에서 코사인 계산이 됐다 — CPU 와 Aurora I/O 를 동시에
 * 먹는 구조였다. pgvector 의 vector(1024) + HNSW 로 다시 지었다(판단 ②).
 *
 * book_title / coverUrl 은 기록 생성 시점 스냅샷이다. catalog_db 는 경계 밖이라
 * 조인할 수 없고, 애초에 조인하면 안 되는 값이다.
 */
@Entity
@Table(name = "lion_memories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LionMemory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lion_memory_id")
    private Long lionMemoryId;

    @Column(name = "lion_id", nullable = false)
    private Long lionId;

    /** catalog_db 경계 밖. FK 없음. */
    @Column(name = "book_id", nullable = false)
    private Long bookId;

    private String bookTitle;

    private String coverUrl;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(columnDefinition = "TEXT")
    private String quoteText;

    private LocalDateTime finishedAt;

    /**
     * Phase 0-2b 확정: Bedrock Titan Text Embeddings V2 / 1024차원.
     * 이 값을 나중에 바꾸면 전건 재임베딩이 필요하므로 재고 소유권과 같은 등급의 결정이다.
     */
    @Column(columnDefinition = "vector(1024)")
    private String embedding;

    private String embeddingModel;

    private Integer embeddingDim;

    public LionMemory(Long lionId, Long bookId, String bookTitle, String coverUrl, String memo, String quoteText) {
        this.lionId = lionId;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.coverUrl = coverUrl;
        this.memo = memo;
        this.quoteText = quoteText;
    }

    public void attachEmbedding(String embedding, String model, int dim) {
        this.embedding = embedding;
        this.embeddingModel = model;
        this.embeddingDim = dim;
    }
}

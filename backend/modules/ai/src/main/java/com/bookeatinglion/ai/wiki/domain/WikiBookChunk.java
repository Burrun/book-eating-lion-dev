package com.bookeatinglion.ai.wiki.domain;

import com.bookeatinglion.ai.wiki.port.VectorIndexPort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 그 책이 인덱스에 넣은 벡터 하나. 키는 {@code {bookId}#{page}#{chunkSeq}} 로 결정적이라
 * 세 값만 있으면 복원된다.
 *
 * <p>존재 이유는 <b>삭제 대상을 알기 위해서</b>다. {@code ListVectors} 에는 prefix 파라미터가
 * 없어서, 이 테이블이 없으면 재적재마다 인덱스를 전수 스캔해야 한다.
 */
@Entity
@Table(name = "wiki_book_chunks")
@IdClass(WikiBookChunk.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WikiBookChunk {

    @Id
    @Column(name = "book_id")
    private Long bookId;

    @Id
    @Column(name = "page")
    private Integer page;

    @Id
    @Column(name = "chunk_seq")
    private Integer chunkSeq;

    public WikiBookChunk(Long bookId, Integer page, Integer chunkSeq) {
        this.bookId = bookId;
        this.page = page;
        this.chunkSeq = chunkSeq;
    }

    public String vectorKey() {
        return VectorIndexPort.key(bookId, page, chunkSeq);
    }

    public static class Key implements Serializable {

        private Long bookId;
        private Integer page;
        private Integer chunkSeq;

        protected Key() {}

        public Key(Long bookId, Integer page, Integer chunkSeq) {
            this.bookId = bookId;
            this.page = page;
            this.chunkSeq = chunkSeq;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            return o instanceof Key k
                    && Objects.equals(bookId, k.bookId)
                    && Objects.equals(page, k.page)
                    && Objects.equals(chunkSeq, k.chunkSeq);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bookId, page, chunkSeq);
        }
    }
}

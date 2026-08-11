package com.bookeatinglion.ai.wiki.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 누가 무엇을 먹였는가. 질의 시 이 목록이 그대로 검색 필터가 되므로 **접근 제어 테이블**이다.
 *
 * <p>벡터는 공용이고 권한만 여기 있다 — 같은 책을 100명이 먹여도 벡터는 1벌이다.
 *
 * <p>PK 가 (member_id, book_id) 라서 먹이기가 멱등이다. 같은 책을 다시 먹여도
 * 행이 늘지 않고 exp 도 중복으로 오르지 않는다.
 */
@Entity
@Table(name = "fed_books")
@IdClass(FedBook.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FedBook {

    /** FK 없음: member_db 경계 밖. */
    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Id
    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "fed_at", nullable = false)
    private LocalDateTime fedAt;

    public FedBook(Long memberId, Long bookId, LocalDateTime fedAt) {
        this.memberId = memberId;
        this.bookId = bookId;
        this.fedAt = fedAt;
    }

    /** 복합 키. record 를 쓰지 않는 이유는 JPA 가 no-arg 생성자를 요구하기 때문이다. */
    public static class Key implements Serializable {

        private Long memberId;
        private Long bookId;

        protected Key() {}

        public Key(Long memberId, Long bookId) {
            this.memberId = memberId;
            this.bookId = bookId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            return o instanceof Key key && Objects.equals(memberId, key.memberId) && Objects.equals(bookId, key.bookId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(memberId, bookId);
        }
    }
}

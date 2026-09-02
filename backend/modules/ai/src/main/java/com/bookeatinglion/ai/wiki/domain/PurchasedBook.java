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
 * order-service 에서 SQS 를 통해 수신한 구매 이벤트. 벡터검색 접근 제어의 근거다.
 */
@Entity
@Table(name = "purchased_books")
@IdClass(PurchasedBook.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchasedBook {

    @Id
    @Column(name = "member_id")
    private String memberId;

    @Id
    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    public PurchasedBook(String memberId, Long bookId, LocalDateTime purchasedAt) {
        this.memberId = memberId;
        this.bookId = bookId;
        this.purchasedAt = purchasedAt;
    }

    public static class Key implements Serializable {

        private String memberId;
        private Long bookId;

        protected Key() {}

        public Key(String memberId, Long bookId) {
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

package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /**
     * order_db.order_items 의 값. FK 가 아니다 — 어느 구매로 얻은 권한인지 추적용.
     */
    @Column(name = "order_item_id")
    private Long orderItemId;

    /**
     * 작성 당시 닉네임 스냅샷. members 를 조인하지 않기 위해 들고 있는다.
     * 사용자가 닉네임을 바꿔도 과거 리뷰는 그대로인 것이 커머스 관례다.
     */
    private String nickname;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder
    public Review(Book book, Long memberId, Long orderItemId, String nickname, int rating, String content) {
        this.book = book;
        this.memberId = memberId;
        this.orderItemId = orderItemId;
        this.nickname = nickname;
        this.rating = rating;
        this.content = content;
    }
}

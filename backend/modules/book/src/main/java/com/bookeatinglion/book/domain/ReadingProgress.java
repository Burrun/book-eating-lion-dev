package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원×도서 단위 전자책 이어읽기 위치. 이력은 남기지 않고 최신 값만 유지한다
 * (RecentViewedBook과 동일한 "최신값 upsert" 모양).
 *
 * memberId(Long) 대신 memberSub(String, Cognito sub)를 쓴다 — member 모듈 Card 도메인이
 * 이미 전환한 최신 회원 식별 방식이고, 카탈로그 모듈에서는 이 엔티티가 첫 적용 사례다.
 */
@Entity
@Table(name = "reading_progress", uniqueConstraints = @UniqueConstraint(columnNames = {"member_sub", "book_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingProgress extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reading_progress_id")
    private Long readingProgressId;

    @Column(name = "member_sub", nullable = false)
    private String memberSub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false)
    private String cfi;

    @Column
    private Integer percentage;

    @Builder
    public ReadingProgress(String memberSub, Book book, String cfi, Integer percentage) {
        this.memberSub = memberSub;
        this.book = book;
        this.cfi = cfi;
        this.percentage = percentage;
    }

    public void updateProgress(String cfi, Integer percentage) {
        this.cfi = cfi;
        this.percentage = percentage;
    }
}

package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recent_books", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "book_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecentViewedBook extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recent_book_id")
    private Long recentBookId;

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false)
    private LocalDateTime viewedAt;

    @Column(nullable = false)
    private int viewCount;

    @Builder
    public RecentViewedBook(String memberId, Book book, LocalDateTime viewedAt) {
        this.memberId = memberId;
        this.book = book;
        this.viewedAt = viewedAt;
        this.viewCount = 1;
    }

    public void touch(LocalDateTime now) {
        this.viewedAt = now;
        this.viewCount++;
    }
}

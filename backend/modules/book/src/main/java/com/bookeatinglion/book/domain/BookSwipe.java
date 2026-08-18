package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "book_swipes", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "book_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookSwipe extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_swipe_id")
    private Long bookSwipeId;

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Enumerated(EnumType.STRING)
    @Column(name = "swipe_action", nullable = false, length = 10)
    private SwipeAction action;

    public BookSwipe(String memberId, Book book, SwipeAction action) {
        this.memberId = memberId;
        this.book = book;
        this.action = action;
    }

    public void changeAction(SwipeAction action) {
        this.action = action;
    }
}

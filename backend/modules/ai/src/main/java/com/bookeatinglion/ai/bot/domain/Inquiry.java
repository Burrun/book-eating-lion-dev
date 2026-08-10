package com.bookeatinglion.ai.bot.domain;

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
 * 1:1 문의. 요구사항 v2 에서 중고거래 채팅을 대체한 기능이다.
 * WebSocket 이 아니라 일반 REST + LLM 응답 봇이므로 sticky session 이 필요 없다.
 */
@Entity
@Table(name = "inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id")
    private Long inquiryId;

    /** member_db 경계 밖. FK 없음. */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 작성 시점 스냅샷. */
    private String nickname;

    /** catalog_db 경계 밖. FK 없음. */
    @Column(name = "book_id")
    private Long bookId;

    /** 생성 시점 스냅샷. 저빈도 쓰기라 값이 없으면 catalog 조회 1회로 충분하다. */
    private String bookTitle;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String adminAnswer;

    @Column(nullable = false)
    private boolean answered;

    private LocalDateTime answeredAt;

    public Inquiry(Long memberId, String nickname, Long bookId, String bookTitle, String title, String content) {
        this.memberId = memberId;
        this.nickname = nickname;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.title = title;
        this.content = content;
        this.answered = false;
    }

    public void answer(String answer, LocalDateTime now) {
        this.adminAnswer = answer;
        this.answered = true;
        this.answeredAt = now;
    }
}

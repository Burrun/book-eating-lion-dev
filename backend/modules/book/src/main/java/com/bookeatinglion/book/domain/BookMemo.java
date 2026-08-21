package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원×도서 단위 완독 요약 메모. 책 하나당 1개만 존재한다(재작성 시 덮어쓴다) —
 * ReadingProgress와 같은 "최신값 upsert" 모양이다.
 *
 * <p>fedAt은 이 메모가 사자에게 먹여져 ai-service 인덱스에 반영됐는지를 나타내는
 * 읽기 전용 마크다 — 실제 EXP/인덱스 상태의 소유권은 ai_db(fed_books)에 있고, 여기
 * 값은 "먹일 수 있는 메모" 목록을 거르기 위한 로컬 사본이다. 재작성해도 fedAt은
 * 유지된다(EXP는 다시 오르지 않는다) — BookMemoService.updateMemo() 참고.
 */
@Entity
@Table(name = "book_memos", uniqueConstraints = @UniqueConstraint(columnNames = {"member_sub", "book_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookMemo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_memo_id")
    private Long bookMemoId;

    @Column(name = "member_sub", nullable = false)
    private String memberSub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "memo_text", nullable = false, length = 4000)
    private String memoText;

    @Column(name = "fed_at")
    private LocalDateTime fedAt;

    @Builder
    public BookMemo(String memberSub, Book book, String memoText) {
        this.memberSub = memberSub;
        this.book = book;
        this.memoText = memoText;
    }

    public void updateText(String memoText) {
        this.memoText = memoText;
    }

    public void markFed(LocalDateTime fedAt) {
        this.fedAt = fedAt;
    }

    public boolean isFed() {
        return fedAt != null;
    }
}

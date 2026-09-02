package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * EPUB 뷰어에서 문장을 더블클릭하거나 드래그해 남기는 하이라이트 메모.
 *
 * 이전의 BookMemo(회원×도서 1개 "완독 요약")를 대체한다 — 그쪽은 upsert 한 행이었지만
 * 하이라이트는 한 책에 여러 개가 쌓이므로 유니크 제약이 없다.
 *
 * 이 텍스트는 벡터로 적재되지 않는다.마이페이지에서 사용자가 다시 읽기 위한 개인 기록일 뿐이다.
 */
@Entity
@Table(name = "book_highlights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookHighlight extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_highlight_id")
    private Long bookHighlightId;

    @Column(name = "member_sub", nullable = false)
    private String memberSub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    /** 선택 구간의 EPUB CFI range. 뷰어가 나중에 원문 위치로 되돌아가는 데 쓴다. */
    @Column(name = "cfi_range", nullable = false, length = 500)
    private String cfiRange;

    /** 사용자가 긁은 원문. 길이 상한은 CatalogHighlightProperties 가 정한다. */
    @Column(name = "selected_text", nullable = false, length = 2000)
    private String selectedText;

    /** 원문에 덧붙인 사용자의 말. 비워둘 수 있다(형광펜만 그은 경우). */
    @Column(name = "memo_text", length = 4000)
    private String memoText;

    @Builder
    public BookHighlight(String memberSub, Book book, String cfiRange, String selectedText, String memoText) {
        this.memberSub = memberSub;
        this.book = book;
        this.cfiRange = cfiRange;
        this.selectedText = selectedText;
        this.memoText = memoText;
    }
}

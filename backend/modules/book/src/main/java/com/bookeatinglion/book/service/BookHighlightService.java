package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.BookHighlight;
import com.bookeatinglion.book.dto.BookHighlightRequest;
import com.bookeatinglion.book.dto.BookHighlightResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.HighlightNotFoundException;
import com.bookeatinglion.book.exception.InvalidHighlightException;
import com.bookeatinglion.book.repository.BookHighlightRepository;
import com.bookeatinglion.book.repository.BookRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EPUB 뷰어에서 긁은 문장 + 메모. BookMemo(회원×도서 1개 upsert)를 대체한다 — 하이라이트는
 * 한 책에 여러 개가 쌓이므로 append 모양이다.
 *
 * <p>사자에게 먹이지 않는다. 먹이기는 이제 "완독한 책"이 대상이고(ReadingProgressService),
 * 메모는 벡터로 적재되지 않는 순수 개인 기록이다.
 */
@Service
@Transactional(readOnly = true)
public class BookHighlightService {

    private final BookHighlightRepository bookHighlightRepository;
    private final BookRepository bookRepository;

    /**
     * 한 번에 긁을 수 있는 원문 길이. "1페이지까지"가 요구사항이지만 epub.js 의 화면 페이지는
     * 창 크기·폰트 크기에 따라 경계가 달라져(같은 책도 기기마다 다르다) 저장 규칙으로 쓸 수
     * 없다 — 기기와 무관한 글자 수로 대신 건다. 실제로 몇 자가 적당한지는 써 보며 조절할
     * 값이라 상수가 아니라 설정이다.
     *
     * 기본값은 여기 두지 않는다 — application.yml 이 유일한 출처다. 양쪽에 두면 한쪽만
     * 고쳤을 때 어느 값이 먹는지 알 수 없다. 설정이 없으면 기동에 실패하는 편이 낫다.
     */
    private final int maxSelectedChars;

    public BookHighlightService(
            BookHighlightRepository bookHighlightRepository,
            BookRepository bookRepository,
            @Value("${catalog.highlight.max-selected-chars}") int maxSelectedChars) {
        this.bookHighlightRepository = bookHighlightRepository;
        this.bookRepository = bookRepository;
        this.maxSelectedChars = maxSelectedChars;
    }

    public int maxSelectedChars() {
        return maxSelectedChars;
    }

    @Transactional
    public BookHighlightResponse create(Long bookId, String memberSub, BookHighlightRequest request) {
        Book book = bookRepository
                .findByBookIdAndIsDeletedFalse(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        String selectedText = request.selectedText().trim();
        if (selectedText.length() > maxSelectedChars) {
            throw new InvalidHighlightException(
                    "한 번에 %d자까지 긁을 수 있습니다(선택 %d자).".formatted(maxSelectedChars, selectedText.length()));
        }

        BookHighlight highlight = bookHighlightRepository.save(BookHighlight.builder()
                .memberSub(memberSub)
                .book(book)
                .cfiRange(request.cfiRange())
                .selectedText(selectedText)
                .memoText(request.memoText())
                .build());
        return BookHighlightResponse.from(highlight);
    }

    /** 마이페이지 "내 메모" 목록 — 책 구분 없이 최신순으로 전부. */
    public List<BookHighlightResponse> listMine(String memberSub) {
        return bookHighlightRepository.findAllByMemberSub(memberSub).stream()
                .map(BookHighlightResponse::from)
                .toList();
    }

    /** 남의 메모는 찾지 못한 것으로 취급한다 — 존재 여부 자체를 알려주지 않는다. */
    @Transactional
    public void delete(Long highlightId, String memberSub) {
        BookHighlight highlight = bookHighlightRepository
                .findByBookHighlightIdAndMemberSub(highlightId, memberSub)
                .orElseThrow(() -> new HighlightNotFoundException(highlightId));
        bookHighlightRepository.delete(highlight);
    }
}

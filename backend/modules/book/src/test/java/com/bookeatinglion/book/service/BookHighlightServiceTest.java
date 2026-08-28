package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.BookHighlight;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookHighlightRequest;
import com.bookeatinglion.book.dto.BookHighlightResponse;
import com.bookeatinglion.book.exception.HighlightNotFoundException;
import com.bookeatinglion.book.exception.InvalidHighlightException;
import com.bookeatinglion.book.repository.BookHighlightRepository;
import com.bookeatinglion.book.repository.BookRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookHighlightServiceTest {

    private static final String MEMBER_SUB = "member-sub-1";
    private static final String CFI_RANGE = "epubcfi(/6/14!/4/2,/1:0,/1:120)";

    /** 설정값이라 생성자로 들어온다. 테스트는 짧게 잡아 경계만 본다. */
    private static final int MAX_SELECTED_CHARS = 10;

    @Mock
    private BookHighlightRepository bookHighlightRepository;

    @Mock
    private BookRepository bookRepository;

    private BookHighlightService bookHighlightService;

    @BeforeEach
    void setUp() {
        bookHighlightService = new BookHighlightService(bookHighlightRepository, bookRepository, MAX_SELECTED_CHARS);
    }

    private Book book(Long id) throws Exception {
        Book book = Book.builder()
                .title("책")
                .author("저자")
                .publisher("출판사")
                .isbn("978130000" + id)
                .category("소설")
                .price(10000)
                .saleStatus(SaleStatus.ON_SALE)
                .publishedDate(LocalDate.now())
                .salesCount(0)
                .build();
        Field idField = Book.class.getDeclaredField("bookId");
        idField.setAccessible(true);
        idField.set(book, id);
        return book;
    }

    @Test
    void 상한_이내면_저장한다() throws Exception {
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book(1L)));
        when(bookHighlightRepository.save(any(BookHighlight.class))).thenAnswer(inv -> inv.getArgument(0));

        BookHighlightResponse saved =
                bookHighlightService.create(1L, MEMBER_SUB, new BookHighlightRequest(CFI_RANGE, "열자까지된다", "메모"));

        assertThat(saved.selectedText()).isEqualTo("열자까지된다");
        assertThat(saved.memoText()).isEqualTo("메모");
    }

    /**
     * "1페이지까지"를 글자 수로 구현한 지점이다. 상한을 넘겨도 조용히 잘라 저장하면 사용자는
     * 자기가 긁은 문장이 어디서 끊겼는지 모른 채 반쪽짜리 인용을 갖게 된다 — 거절한다.
     */
    @Test
    void 상한을_넘으면_거절한다() throws Exception {
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book(1L)));

        assertThatThrownBy(() -> bookHighlightService.create(
                        1L, MEMBER_SUB, new BookHighlightRequest(CFI_RANGE, "열자를넘기는긴문장이다", null)))
                .isInstanceOf(InvalidHighlightException.class);
        verify(bookHighlightRepository, never()).save(any());
    }

    /** 앞뒤 공백은 선택 과정에서 딸려 들어오기 쉬우므로 길이를 재기 전에 턴다. */
    @Test
    void 앞뒤_공백은_길이_계산_전에_제거한다() throws Exception {
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book(1L)));
        when(bookHighlightRepository.save(any(BookHighlight.class))).thenAnswer(inv -> inv.getArgument(0));

        BookHighlightResponse saved =
                bookHighlightService.create(1L, MEMBER_SUB, new BookHighlightRequest(CFI_RANGE, "   열자까지된다   ", null));

        assertThat(saved.selectedText()).isEqualTo("열자까지된다");
    }

    /** 남의 메모는 "없음"으로 답한다 — 404 와 403 을 구분해 주면 존재 여부가 새어나간다. */
    @Test
    void 남의_메모는_삭제할_수_없다() {
        when(bookHighlightRepository.findByBookHighlightIdAndMemberSub(9L, MEMBER_SUB))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookHighlightService.delete(9L, MEMBER_SUB))
                .isInstanceOf(HighlightNotFoundException.class);
        verify(bookHighlightRepository, never()).delete(any());
    }
}

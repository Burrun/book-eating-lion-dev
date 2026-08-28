package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.ReadingProgress;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.FeedableBookResponse;
import com.bookeatinglion.book.dto.ReadingProgressRequest;
import com.bookeatinglion.book.dto.ReadingProgressResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ReadingProgressRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadingProgressServiceTest {

    private static final String MEMBER_SUB = "member-sub-1";

    /** 완독 임계값은 설정값이라 생성자로 들어온다 — 테스트는 운영 기본값과 같은 95를 쓴다. */
    private static final int COMPLETION_PERCENTAGE = 95;

    @Mock
    private ReadingProgressRepository readingProgressRepository;

    @Mock
    private BookRepository bookRepository;

    private ReadingProgressService readingProgressService;

    @BeforeEach
    void setUp() {
        readingProgressService =
                new ReadingProgressService(readingProgressRepository, bookRepository, COMPLETION_PERCENTAGE);
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
    void 처음_기록하는_위치는_새로_저장한다() throws Exception {
        Book book = book(1L);
        when(readingProgressRepository.findByMemberSubAndBook_BookId(MEMBER_SUB, 1L))
                .thenReturn(Optional.empty());
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book));
        when(readingProgressRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReadingProgressResponse response =
                readingProgressService.saveProgress(1L, MEMBER_SUB, new ReadingProgressRequest("epubcfi(/6/2)", 12));

        verify(readingProgressRepository, times(1)).save(any());
        assertThat(response.cfi()).isEqualTo("epubcfi(/6/2)");
        assertThat(response.percentage()).isEqualTo(12);
    }

    @Test
    void 기존_기록이_있으면_새로_저장하지_않고_값만_갱신한다() throws Exception {
        Book book = book(1L);
        ReadingProgress existing = ReadingProgress.builder()
                .memberSub(MEMBER_SUB)
                .book(book)
                .cfi("epubcfi(/6/2)")
                .percentage(10)
                .build();
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book));
        when(readingProgressRepository.findByMemberSubAndBook_BookId(MEMBER_SUB, 1L))
                .thenReturn(Optional.of(existing));

        ReadingProgressResponse response =
                readingProgressService.saveProgress(1L, MEMBER_SUB, new ReadingProgressRequest("epubcfi(/6/4)", 55));

        verify(readingProgressRepository, never()).save(any());
        assertThat(response.cfi()).isEqualTo("epubcfi(/6/4)");
        assertThat(response.percentage()).isEqualTo(55);
    }

    @Test
    void 삭제된_도서에_위치_저장은_예외를_던진다() {
        when(bookRepository.findByBookIdAndIsDeletedFalse(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> readingProgressService.saveProgress(
                        999L, MEMBER_SUB, new ReadingProgressRequest("epubcfi(/6/2)", null)))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 기록이_없으면_null을_반환한다() {
        when(bookRepository.existsByBookIdAndIsDeletedFalse(1L)).thenReturn(true);
        when(readingProgressRepository.findByMemberSubAndBook_BookId(MEMBER_SUB, 1L))
                .thenReturn(Optional.empty());

        ReadingProgressResponse response = readingProgressService.getProgress(1L, MEMBER_SUB);

        assertThat(response).isNull();
    }

    @Test
    void 기록이_있으면_저장된_위치를_반환한다() throws Exception {
        Book book = book(1L);
        ReadingProgress existing = ReadingProgress.builder()
                .memberSub(MEMBER_SUB)
                .book(book)
                .cfi("epubcfi(/6/2)")
                .percentage(40)
                .build();
        when(bookRepository.existsByBookIdAndIsDeletedFalse(1L)).thenReturn(true);
        when(readingProgressRepository.findByMemberSubAndBook_BookId(MEMBER_SUB, 1L))
                .thenReturn(Optional.of(existing));

        ReadingProgressResponse response = readingProgressService.getProgress(1L, MEMBER_SUB);

        assertThat(response.percentage()).isEqualTo(40);
    }

    @Test
    void 삭제된_도서_조회는_예외를_던진다() {
        when(bookRepository.existsByBookIdAndIsDeletedFalse(999L)).thenReturn(false);

        assertThatThrownBy(() -> readingProgressService.getProgress(999L, MEMBER_SUB))
                .isInstanceOf(BookNotFoundException.class);
    }

    /** 먹이기 카드는 완독 임계값을 그대로 쿼리에 넘겨야 한다 — 서비스가 다시 거르지 않는다. */
    @Test
    void 먹일_수_있는_책은_설정된_완독_임계값으로_조회한다() throws Exception {
        ReadingProgress progress = ReadingProgress.builder()
                .memberSub(MEMBER_SUB)
                .book(book(1L))
                .cfi("epubcfi(/6/2!/4/2)")
                .percentage(97)
                .build();
        when(readingProgressRepository.findFeedable(MEMBER_SUB, COMPLETION_PERCENTAGE))
                .thenReturn(List.of(progress));

        List<FeedableBookResponse> feedable = readingProgressService.listFeedableBooks(MEMBER_SUB);

        assertThat(feedable).hasSize(1);
        assertThat(feedable.getFirst().bookId()).isEqualTo(1L);
        assertThat(feedable.getFirst().percentage()).isEqualTo(97);
    }

    /** 먹인 표시는 멱등하다 — 재시도로 두 번 불려도 예외가 아니라 시각만 덮어쓴다. */
    @Test
    void 먹인_표시는_두_번_불려도_예외가_아니다() throws Exception {
        ReadingProgress progress = ReadingProgress.builder()
                .memberSub(MEMBER_SUB)
                .book(book(1L))
                .cfi("epubcfi(/6/2!/4/2)")
                .percentage(100)
                .build();
        when(readingProgressRepository.findByMemberSubAndBook_BookId(MEMBER_SUB, 1L))
                .thenReturn(Optional.of(progress));

        readingProgressService.markFed(1L, MEMBER_SUB);
        LocalDateTime first = progress.getFedAt();
        readingProgressService.markFed(1L, MEMBER_SUB);

        assertThat(first).isNotNull();
        assertThat(progress.getFedAt()).isNotNull();
    }
}

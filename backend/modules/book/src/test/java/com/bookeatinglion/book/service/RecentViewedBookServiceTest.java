package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.RecentViewedBook;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.RecentViewedBookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecentViewedBookServiceTest {

    @Mock
    private RecentViewedBookRepository recentViewedBookRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private RecentViewedBookService recentViewedBookService;

    private Book book(Long id) throws Exception {
        Book book = Book.builder()
                .title("책").author("저자").publisher("출판사").isbn("978130000" + id)
                .category("소설").price(10000)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build();
        Field idField = Book.class.getDeclaredField("bookId");
        idField.setAccessible(true);
        idField.set(book, id);
        return book;
    }

    @Test
    void 처음_보는_책은_새로_기록한다() throws Exception {
        Book book = book(1L);
        when(recentViewedBookRepository.findByMemberIdAndBook_BookId(1L, 1L)).thenReturn(Optional.empty());
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        recentViewedBookService.recordView(1L, 1L);

        verify(recentViewedBookRepository, times(1)).save(any());
    }

    @Test
    void 다시_보는_책은_viewedAt만_갱신하고_새로_저장하지_않는다() throws Exception {
        Book book = book(1L);
        RecentViewedBook existing = RecentViewedBook.builder()
                .memberId(1L).book(book).viewedAt(LocalDateTime.now().minusDays(1)).build();
        when(recentViewedBookRepository.findByMemberIdAndBook_BookId(1L, 1L)).thenReturn(Optional.of(existing));

        recentViewedBookService.recordView(1L, 1L);

        verify(recentViewedBookRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_책_기록은_예외를_던진다() {
        when(recentViewedBookRepository.findByMemberIdAndBook_BookId(1L, 999L)).thenReturn(Optional.empty());
        when(bookRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recentViewedBookService.recordView(999L, 1L))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 내_최근_본_책_목록을_책_요약_정보로_조회한다() throws Exception {
        Book book = book(1L);
        when(recentViewedBookRepository.findByMemberIdOrderByViewedAtDesc(any(), any()))
                .thenReturn(List.of(RecentViewedBook.builder()
                        .memberId(1L).book(book).viewedAt(LocalDateTime.now()).build()));

        List<BookSummaryResponse> result = recentViewedBookService.getMyRecentBooks(1L, 20);

        assertThat(result).extracting(BookSummaryResponse::title).containsExactly("책");
    }
}
